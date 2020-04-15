/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.1
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package spinal.sim.vpi;

public class SharedMemIface {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected SharedMemIface(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(SharedMemIface obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  @SuppressWarnings("deprecation")
  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        JNISharedMemIfaceJNI.delete_SharedMemIface(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public SharedMemIface(String shmem_name_, long shmem_size_) {
    this(JNISharedMemIfaceJNI.new_SharedMemIface(shmem_name_, shmem_size_), true);
  }

  public String print_signals() {
    return JNISharedMemIfaceJNI.SharedMemIface_print_signals(swigCPtr, this);
  }

  public long get_signal_handle(String handle_name) {
    return JNISharedMemIfaceJNI.SharedMemIface_get_signal_handle(swigCPtr, this, handle_name);
  }

  public VectorInt8 read(long handle) {
    return new VectorInt8(JNISharedMemIfaceJNI.SharedMemIface_read__SWIG_0(swigCPtr, this, handle), true);
  }

  public void read(long handle, VectorInt8 data) {
    JNISharedMemIfaceJNI.SharedMemIface_read__SWIG_1(swigCPtr, this, handle, VectorInt8.getCPtr(data), data);
  }

  public long read64(long handle) {
    return JNISharedMemIfaceJNI.SharedMemIface_read64(swigCPtr, this, handle);
  }

  public int read32(long handle) {
    return JNISharedMemIfaceJNI.SharedMemIface_read32(swigCPtr, this, handle);
  }

  public void write(long handle, VectorInt8 data) {
    JNISharedMemIfaceJNI.SharedMemIface_write(swigCPtr, this, handle, VectorInt8.getCPtr(data), data);
  }

  public void write64(long handle, long data) {
    JNISharedMemIfaceJNI.SharedMemIface_write64(swigCPtr, this, handle, data);
  }

  public void write32(long handle, int data) {
    JNISharedMemIfaceJNI.SharedMemIface_write32(swigCPtr, this, handle, data);
  }

  public void sleep(long sleep_cycles) {
    JNISharedMemIfaceJNI.SharedMemIface_sleep(swigCPtr, this, sleep_cycles);
  }

  public void eval() {
    JNISharedMemIfaceJNI.SharedMemIface_eval(swigCPtr, this);
  }

  public void randomize(long seed) {
    JNISharedMemIfaceJNI.SharedMemIface_randomize(swigCPtr, this, seed);
  }

  public void close() {
    JNISharedMemIfaceJNI.SharedMemIface_close(swigCPtr, this);
  }

  public void check_ready() {
    JNISharedMemIfaceJNI.SharedMemIface_check_ready(swigCPtr, this);
  }

  public boolean is_closed() {
    return JNISharedMemIfaceJNI.SharedMemIface_is_closed(swigCPtr, this);
  }

}
