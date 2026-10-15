import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('synergy3_setup', Path(__file__).parents[1] / 'synergy3_setup.py')
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)
FP = 'ab' * 32


def fixture():
    primary = {'id': 'a' * 64, 'name': 'MacBook', 'isRemoved': False,
               'misc': {'left': 200, 'top': 200, 'width': 200, 'height': 110, 'tlsFingerprint': 'cd' * 32}}
    database = {'version': {'schemaVersion': 17, 'syncVersion': 1},
                'data': {'mainID': {'id': primary['id']}, 'computers': [primary],
                         'security': {'encryption': True}, 'override': {'shouldOverride': False},
                         'hotkeys': {'unrelated': 'keep this'}, 'mouse': {'relative': True}}}
    local = {'version': 8, 'myId': primary['id'], 'serial': 'preserve-private-license',
             'local_security': {'require_client_certs': True, 'path': '/keep/server.pem',
                                'trusted_peers': [{'fingerprint': 'ef' * 32, 'computerId': 'b' * 64, 'name': 'Other Mac'}]}}
    return database, local


class SetupTest(unittest.TestCase):
    def setUp(self):
        addresses = patch.object(setup, 'detect_lan_addresses', return_value=[('en0', '192.168.1.10')])
        addresses.start()
        self.addCleanup(addresses.stop)

    def test_status_uses_live_core_state_not_saved_connected_flag(self):
        database, local, _ = setup.prepare(*fixture(), 'Phone', FP)
        settings = database['data']
        phone = settings['computers'][-1]
        # The placeholder written at registration is not a live connection status.
        phone['misc']['connected'] = False
        settings['serial'] = local['serial']
        settings['screenStatus'] = {phone['id']: {'core': 'connected', 'serviceReachable': False}}
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            setup.show_status(settings, 'Phone')
        self.assertIn('Keyboard/mouse connection: CONNECTED', output.getvalue())
        self.assertIn('Synergy desktop management link: not connected (expected for Input Leaf)', output.getvalue())
        self.assertNotIn(local['serial'], output.getvalue())
        # Conversely, a stored true flag and reachable service cannot mask a lost input link.
        phone['misc']['connected'] = True
        settings['screenStatus'][phone['id']] = {'core': 'disconnected', 'serviceReachable': True}
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            setup.show_status(settings, 'Phone')
        self.assertIn('Keyboard/mouse connection: DISCONNECTED', output.getvalue())

    def test_status_handles_missing_runtime_state_and_requires_unambiguous_device(self):
        database, _, _ = setup.prepare(*fixture(), 'Phone', FP)
        settings = database['data']
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            setup.show_status(settings, 'Phone')
        self.assertIn('Keyboard/mouse connection: UNKNOWN', output.getvalue())
        with self.assertRaises(ValueError): setup.show_status(settings, 'Absent')
        settings['computers'].append(copy.deepcopy(settings['computers'][-1]))
        with self.assertRaises(ValueError): setup.show_status(settings, 'Phone')
        settings['computers'][-1]['isRemoved'] = True
        with patch('sys.stdout', new_callable=io.StringIO): setup.show_status(settings, 'Phone')

    def test_status_command_needs_no_fingerprint_and_reads_current_screen_name(self):
        database, _, _ = setup.prepare(*fixture(), 'Phone', FP)
        settings = database['data']
        phone = settings['computers'][-1]
        phone['name'] = 'Renamed Phone'
        with patch('sys.argv', ['synergy3_setup.py', '--status', '--name', 'Renamed Phone']), \
             patch.object(setup, 'read_live_settings', return_value=settings), \
             patch.object(setup, 'apply_changes') as apply, \
             patch('sys.stdout', new_callable=io.StringIO) as output:
            setup.main()
        self.assertIn(setup.core_name(phone), output.getvalue())
        apply.assert_not_called()

    def test_status_command_reports_unavailable_service_without_dumping_response(self):
        with patch('sys.argv', ['synergy3_setup.py', '--status']), \
             patch.object(setup, 'read_live_settings', side_effect=OSError('private diagnostic')), \
             patch('sys.stderr', new_callable=io.StringIO) as output:
            with self.assertRaises(SystemExit) as failure: setup.main()
        self.assertEqual(failure.exception.code, 1)
        self.assertIn('Start Synergy on the primary Mac', output.getvalue())
        self.assertNotIn('private diagnostic', output.getvalue())

    def test_registration_preserves_other_computers_settings_and_trust(self):
        database, local = fixture()
        original = copy.deepcopy((database, local))
        updated, new_local, name = setup.prepare(database, local, 'Android Phone', FP)
        phone = updated['data']['computers'][-1]
        self.assertRegex(name, r'^androidphone-[0-9a-f]{8}$')
        self.assertEqual(phone['misc']['top'], 311)
        self.assertEqual(updated['data']['computers'][0], database['data']['computers'][0])
        self.assertEqual(updated['data']['hotkeys'], database['data']['hotkeys'])
        self.assertEqual(new_local['serial'], local['serial'])
        self.assertTrue(new_local['local_security']['require_client_certs'])
        self.assertEqual(new_local['local_security']['trusted_peers'][0], local['local_security']['trusted_peers'][0])
        self.assertEqual(new_local['local_security']['trusted_peers'][-1]['fingerprint'], FP)
        self.assertEqual((database, local), original)
        self.assertGreater(updated['version']['syncVersion'], database['version']['syncVersion'])

    def test_rerun_is_idempotent_and_regeneration_keeps_layout_and_identity(self):
        first, local, name = setup.prepare(*fixture(), 'Phone', FP)
        second, local2, name2 = setup.prepare(first, local, 'Phone', FP)
        self.assertEqual((first, local, name), (second, local2, name2))
        second, local2, name2 = setup.prepare(first, local, 'Phone', '12' * 32)
        self.assertEqual(name, name2)
        self.assertEqual(second['data']['computers'][-1]['misc']['top'], 311)
        self.assertEqual(len(local2['local_security']['trusted_peers']), 2)
        self.assertEqual(local2['local_security']['trusted_peers'][-1]['fingerprint'], '12' * 32)

    def test_rejects_occupied_edge_duplicate_names_and_reused_certificate(self):
        database, local = fixture()
        other = copy.deepcopy(database['data']['computers'][0])
        other.update(id='b' * 64, name='Other Mac')
        other['misc']['top'] = 311
        database['data']['computers'].append(other)
        with self.assertRaisesRegex(ValueError, 'occupied'):
            setup.prepare(database, local, 'Phone', FP)
        with self.assertRaisesRegex(ValueError, 'display name'):
            setup.prepare(database, local, 'Other Mac', FP, 'above')
        with self.assertRaisesRegex(ValueError, 'different computer'):
            setup.prepare(database, local, 'Phone', 'ef' * 32, 'above')
        setup.prepare(database, local, 'Phone', FP, 'above')

    def test_rejects_unverified_schemas_custom_configs_and_wrong_primary(self):
        for field, value in [('schema', 18), ('override', True), ('encryption', False), ('primary', 'other')]:
            database, local = fixture()
            if field == 'schema': database['version']['schemaVersion'] = value
            elif field == 'primary': local['myId'] = value
            elif field == 'override': database['data']['override']['shouldOverride'] = value
            else: database['data']['security']['encryption'] = value
            with self.assertRaises(ValueError): setup.prepare(database, local, 'Phone', FP)

    def test_requires_full_fingerprint(self):
        self.assertEqual(setup.fingerprint(':'.join(['AB'] * 32)), FP)
        for value in ['abcd', 'gh' * 32, '', '../test', FP + '00']:
            with self.assertRaises(ValueError): setup.fingerprint(value)

    def test_backups_are_exact_private_and_restore_after_failed_write(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            originals = {'db.json': b'{"original":1}', 'local.json': b'{"private":2}'}
            for name, value in originals.items(): (directory / name).write_bytes(value)
            with patch.object(setup, 'require_stopped'):
                backup = setup.apply_changes(directory, originals, {'db.json': {'new': 1}, 'local.json': {'new': 2}})
            for name, value in originals.items():
                self.assertEqual((backup / name).read_bytes(), value)
                self.assertEqual(os.stat(backup / name).st_mode & 0o777, 0o600)
            self.assertEqual(os.stat(backup).st_mode & 0o777, 0o700)
            updated = {name: (directory / name).read_bytes() for name in originals}
            real_write = setup.atomic_write
            def fail_one_write(path, contents):
                if path == directory / 'local.json' and b'fail' in contents: raise OSError('simulated failure')
                real_write(path, contents)
            with patch.object(setup, 'require_stopped'), patch.object(setup, 'atomic_write', side_effect=fail_one_write):
                with self.assertRaises(OSError):
                    setup.apply_changes(directory, updated, {'db.json': {}, 'local.json': {'fail': True}})
            self.assertEqual({name: (directory / name).read_bytes() for name in originals}, updated)

    def test_refuses_running_service_or_stale_files(self):
        with patch.object(setup.subprocess, 'run') as run:
            run.return_value.stdout = '/Applications/Synergy.app/Contents/MacOS/synergy-service\n'
            with self.assertRaisesRegex(ValueError, 'background service'): setup.require_stopped()
        with tempfile.TemporaryDirectory() as tmp, patch.object(setup, 'require_stopped'):
            directory = Path(tmp)
            (directory / 'db.json').write_bytes(b'new')
            with self.assertRaisesRegex(ValueError, 'Settings changed'):
                setup.apply_changes(directory, {'db.json': b'old'}, {})


class AddressHintsTest(unittest.TestCase):
    def test_selects_active_lan_addresses_without_tunnels_or_loopback(self):
        output = '''lo0: flags=8049<UP,LOOPBACK,RUNNING,MULTICAST> mtu 16384
    inet 127.0.0.1 netmask 0xff000000
en0: flags=8863<UP,BROADCAST,RUNNING,MULTICAST> mtu 1500
    inet6 fe80::1%en0 prefixlen 64
    inet 192.168.1.10 netmask 0xffffff00 broadcast 192.168.1.255
    inet 192.168.1.10 netmask 0xffffff00 broadcast 192.168.1.255
    status: active
en1: flags=8863<UP,BROADCAST,RUNNING,MULTICAST> mtu 1500
    inet 10.0.0.5 netmask 0xffffff00
    status: active
utun4: flags=8051<UP,POINTOPOINT,RUNNING,MULTICAST> mtu 1380
    inet 100.64.0.1 --> 100.64.0.1 netmask 0xffff0000
awdl0: flags=8943<UP,BROADCAST,RUNNING,MULTICAST> mtu 1500
    inet 10.1.0.1 netmask 0xffffff00
bridge0: flags=8863<UP,BROADCAST,RUNNING,MULTICAST> mtu 1500
    inet 192.168.2.1 netmask 0xffffff00
    status: inactive
en2: flags=8862<BROADCAST,RUNNING,MULTICAST> mtu 1500
    inet 10.2.0.1 netmask 0xffffff00
    status: inactive
'''
        self.assertEqual(setup.lan_addresses_from_ifconfig(output), [('en0', '192.168.1.10'), ('en1', '10.0.0.5')])

    def test_rejects_unusable_ipv4_but_allows_lan_bridges(self):
        for value in ['127.0.0.1', '169.254.1.2', '0.0.0.0', '224.0.0.1', '255.255.255.255', '::1', 'invalid']:
            self.assertFalse(setup.usable_ipv4(value), value)
        self.assertEqual(setup.lan_addresses_from_ifconfig('''bridge0: flags=8863<UP,BROADCAST,RUNNING,MULTICAST> mtu 1500
    inet 169.254.1.2 netmask 0xffff0000
    inet 192.168.2.10 netmask 0xffffff00
    status: active
'''), [('bridge0', '192.168.2.10')])

    def test_interface_lookup_failures_do_not_prevent_setup_instructions(self):
        for failure in [OSError('unavailable'), setup.subprocess.TimeoutExpired('ifconfig', 3)]:
            with patch.object(setup.subprocess, 'run', side_effect=failure):
                self.assertEqual(setup.detect_lan_addresses(), [])
                with patch('sys.stdout', new_callable=io.StringIO) as output:
                    setup.show_mac_settings({})
            self.assertIn('LAN IP not detected', output.getvalue())
            self.assertIn('Fingerprint', output.getvalue())

    def test_secondary_mac_does_not_present_its_address_or_fingerprint_as_the_primary(self):
        database, local = fixture()
        settings = database['data']
        settings['myId'] = local['myId']
        other = {'id': 'b' * 64, 'name': 'Other Mac', 'misc': {'tlsFingerprint': '12' * 32}}
        settings['computers'].append(other)
        settings['mainID']['id'] = other['id']
        settings['local_computers'] = {other['id']: '192.168.1.68'}
        with patch.object(setup, 'detect_lan_addresses', return_value=[('en0', '192.168.1.10')]), \
             patch('sys.stdout', new_callable=io.StringIO) as output:
            setup.show_mac_settings(settings)
        result = output.getvalue()
        self.assertIn('This Mac is SECONDARY', result)
        self.assertIn('current primary, Other Mac', result)
        self.assertIn('last-known LAN IP: 192.168.1.68', result)
        self.assertIn('192.168.1.10 (en0)', result)
        self.assertIn(':'.join(['CD'] * 32), result)
        self.assertNotIn(':'.join(['12'] * 32), result)
        self.assertIn('only when connecting to THIS Mac', result)

    def test_primary_mac_displays_its_own_fingerprint_and_malformed_values_are_not_trusted(self):
        database, local = fixture()
        settings = {**database['data'], 'myId': local['myId']}
        with patch.object(setup, 'detect_lan_addresses', return_value=[('en0', '192.168.1.10')]), \
             patch('sys.stdout', new_callable=io.StringIO) as output:
            setup.show_mac_settings(settings)
        self.assertIn(':'.join(['CD'] * 32), output.getvalue())
        self.assertNotIn('SECONDARY', output.getvalue())
        settings['computers'][0]['misc']['tlsFingerprint'] = 'not-a-fingerprint'
        with patch.object(setup, 'detect_lan_addresses', return_value=[]), \
             patch('sys.stdout', new_callable=io.StringIO) as output:
            setup.show_mac_settings(settings)
        self.assertIn('fingerprint is not available', output.getvalue())
        self.assertNotIn('not-a-fingerprint', output.getvalue())


if __name__ == '__main__':
    unittest.main()
