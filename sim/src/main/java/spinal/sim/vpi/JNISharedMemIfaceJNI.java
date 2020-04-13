/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.1
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package spinal.sim.vpi;

public class JNISharedMemIfaceJNI {
  public final static native long new_VectorInt8__SWIG_0();
  public final static native long new_VectorInt8__SWIG_1(long jarg1, VectorInt8 jarg1_);
  public final static native long VectorInt8_capacity(long jarg1, VectorInt8 jarg1_);
  public final static native void VectorInt8_reserve(long jarg1, VectorInt8 jarg1_, long jarg2);
  public final static native boolean VectorInt8_isEmpty(long jarg1, VectorInt8 jarg1_);
  public final static native void VectorInt8_clear(long jarg1, VectorInt8 jarg1_);
  public final static native long new_VectorInt8__SWIG_2(int jarg1, byte jarg2);
  public final static native int VectorInt8_doSize(long jarg1, VectorInt8 jarg1_);
  public final static native void VectorInt8_doAdd__SWIG_0(long jarg1, VectorInt8 jarg1_, byte jarg2);
  public final static native void VectorInt8_doAdd__SWIG_1(long jarg1, VectorInt8 jarg1_, int jarg2, byte jarg3);
  public final static native byte VectorInt8_doRemove(long jarg1, VectorInt8 jarg1_, int jarg2);
  public final static native byte VectorInt8_doGet(long jarg1, VectorInt8 jarg1_, int jarg2);
  public final static native byte VectorInt8_doSet(long jarg1, VectorInt8 jarg1_, int jarg2, byte jarg3);
  public final static native void VectorInt8_doRemoveRange(long jarg1, VectorInt8 jarg1_, int jarg2, int jarg3);
  public final static native void delete_VectorInt8(long jarg1);
  public final static native long new_SharedMemIface(String jarg1, long jarg2);
  public final static native void delete_SharedMemIface(long jarg1);
  public final static native String SharedMemIface_print_signals(long jarg1, SharedMemIface jarg1_);
  public final static native long SharedMemIface_get_signal_handle(long jarg1, SharedMemIface jarg1_, String jarg2);
  public final static native long SharedMemIface_read__SWIG_0(long jarg1, SharedMemIface jarg1_, long jarg2);
  public final static native void SharedMemIface_read__SWIG_1(long jarg1, SharedMemIface jarg1_, long jarg2, long jarg3, VectorInt8 jarg3_);
  public final static native long SharedMemIface_read64(long jarg1, SharedMemIface jarg1_, long jarg2);
  public final static native int SharedMemIface_read32(long jarg1, SharedMemIface jarg1_, long jarg2);
  public final static native void SharedMemIface_write(long jarg1, SharedMemIface jarg1_, long jarg2, long jarg3, VectorInt8 jarg3_);
  public final static native void SharedMemIface_write64(long jarg1, SharedMemIface jarg1_, long jarg2, long jarg3);
  public final static native void SharedMemIface_write32(long jarg1, SharedMemIface jarg1_, long jarg2, int jarg3);
  public final static native void SharedMemIface_sleep(long jarg1, SharedMemIface jarg1_, long jarg2);
  public final static native void SharedMemIface_eval(long jarg1, SharedMemIface jarg1_);
  public final static native void SharedMemIface_close(long jarg1, SharedMemIface jarg1_);
  public final static native boolean SharedMemIface_is_closed(long jarg1, SharedMemIface jarg1_);
}
