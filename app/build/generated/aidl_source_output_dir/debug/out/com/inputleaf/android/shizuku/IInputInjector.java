/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.inputleaf.android.shizuku;
/**
 * AIDL interface for injecting input events via Shizuku.
 * This service runs with shell (ADB) privileges and can call InputManager.injectInputEvent().
 */
public interface IInputInjector extends android.os.IInterface
{
  /** Default implementation for IInputInjector. */
  public static class Default implements com.inputleaf.android.shizuku.IInputInjector
  {
    /**
     * Inject a mouse/touch motion event.
     * @param action MotionEvent action (ACTION_DOWN, ACTION_MOVE, ACTION_UP, etc.)
     * @param x X coordinate
     * @param y Y coordinate
     * @param buttonState Mouse button state (BUTTON_PRIMARY, BUTTON_SECONDARY, etc.)
     * @return true if injection succeeded
     */
    @Override public boolean injectMotionEvent(int action, float x, float y, int buttonState) throws android.os.RemoteException
    {
      return false;
    }
    /**
     * Inject a scroll event (mouse wheel).
     * @param x X coordinate
     * @param y Y coordinate
     * @param hScroll Horizontal scroll amount (-1 to 1)
     * @param vScroll Vertical scroll amount (-1 to 1)
     * @return true if injection succeeded
     */
    @Override public boolean injectScrollEvent(float x, float y, float hScroll, float vScroll) throws android.os.RemoteException
    {
      return false;
    }
    /**
     * Inject a key event (keyboard).
     * @param action KeyEvent action (ACTION_DOWN or ACTION_UP)
     * @param keyCode Android KeyEvent keycode
     * @param metaState Modifier state (CTRL, ALT, SHIFT, etc.)
     * @return true if injection succeeded
     */
    @Override public boolean injectKeyEvent(int action, int keyCode, int metaState) throws android.os.RemoteException
    {
      return false;
    }
    /** Destroy the service and release resources. */
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.inputleaf.android.shizuku.IInputInjector
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.inputleaf.android.shizuku.IInputInjector interface,
     * generating a proxy if needed.
     */
    public static com.inputleaf.android.shizuku.IInputInjector asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.inputleaf.android.shizuku.IInputInjector))) {
        return ((com.inputleaf.android.shizuku.IInputInjector)iin);
      }
      return new com.inputleaf.android.shizuku.IInputInjector.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_injectMotionEvent:
        {
          int _arg0;
          _arg0 = data.readInt();
          float _arg1;
          _arg1 = data.readFloat();
          float _arg2;
          _arg2 = data.readFloat();
          int _arg3;
          _arg3 = data.readInt();
          boolean _result = this.injectMotionEvent(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_injectScrollEvent:
        {
          float _arg0;
          _arg0 = data.readFloat();
          float _arg1;
          _arg1 = data.readFloat();
          float _arg2;
          _arg2 = data.readFloat();
          float _arg3;
          _arg3 = data.readFloat();
          boolean _result = this.injectScrollEvent(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_injectKeyEvent:
        {
          int _arg0;
          _arg0 = data.readInt();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          boolean _result = this.injectKeyEvent(_arg0, _arg1, _arg2);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.inputleaf.android.shizuku.IInputInjector
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      /**
       * Inject a mouse/touch motion event.
       * @param action MotionEvent action (ACTION_DOWN, ACTION_MOVE, ACTION_UP, etc.)
       * @param x X coordinate
       * @param y Y coordinate
       * @param buttonState Mouse button state (BUTTON_PRIMARY, BUTTON_SECONDARY, etc.)
       * @return true if injection succeeded
       */
      @Override public boolean injectMotionEvent(int action, float x, float y, int buttonState) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(action);
          _data.writeFloat(x);
          _data.writeFloat(y);
          _data.writeInt(buttonState);
          boolean _status = mRemote.transact(Stub.TRANSACTION_injectMotionEvent, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /**
       * Inject a scroll event (mouse wheel).
       * @param x X coordinate
       * @param y Y coordinate
       * @param hScroll Horizontal scroll amount (-1 to 1)
       * @param vScroll Vertical scroll amount (-1 to 1)
       * @return true if injection succeeded
       */
      @Override public boolean injectScrollEvent(float x, float y, float hScroll, float vScroll) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeFloat(x);
          _data.writeFloat(y);
          _data.writeFloat(hScroll);
          _data.writeFloat(vScroll);
          boolean _status = mRemote.transact(Stub.TRANSACTION_injectScrollEvent, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /**
       * Inject a key event (keyboard).
       * @param action KeyEvent action (ACTION_DOWN or ACTION_UP)
       * @param keyCode Android KeyEvent keycode
       * @param metaState Modifier state (CTRL, ALT, SHIFT, etc.)
       * @return true if injection succeeded
       */
      @Override public boolean injectKeyEvent(int action, int keyCode, int metaState) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(action);
          _data.writeInt(keyCode);
          _data.writeInt(metaState);
          boolean _status = mRemote.transact(Stub.TRANSACTION_injectKeyEvent, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** Destroy the service and release resources. */
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_injectMotionEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_injectScrollEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_injectKeyEvent = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
  }
  public static final java.lang.String DESCRIPTOR = "com.inputleaf.android.shizuku.IInputInjector";
  /**
   * Inject a mouse/touch motion event.
   * @param action MotionEvent action (ACTION_DOWN, ACTION_MOVE, ACTION_UP, etc.)
   * @param x X coordinate
   * @param y Y coordinate
   * @param buttonState Mouse button state (BUTTON_PRIMARY, BUTTON_SECONDARY, etc.)
   * @return true if injection succeeded
   */
  public boolean injectMotionEvent(int action, float x, float y, int buttonState) throws android.os.RemoteException;
  /**
   * Inject a scroll event (mouse wheel).
   * @param x X coordinate
   * @param y Y coordinate
   * @param hScroll Horizontal scroll amount (-1 to 1)
   * @param vScroll Vertical scroll amount (-1 to 1)
   * @return true if injection succeeded
   */
  public boolean injectScrollEvent(float x, float y, float hScroll, float vScroll) throws android.os.RemoteException;
  /**
   * Inject a key event (keyboard).
   * @param action KeyEvent action (ACTION_DOWN or ACTION_UP)
   * @param keyCode Android KeyEvent keycode
   * @param metaState Modifier state (CTRL, ALT, SHIFT, etc.)
   * @return true if injection succeeded
   */
  public boolean injectKeyEvent(int action, int keyCode, int metaState) throws android.os.RemoteException;
  /** Destroy the service and release resources. */
  public void destroy() throws android.os.RemoteException;
}
