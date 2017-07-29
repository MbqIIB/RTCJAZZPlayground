package com.ibm.team.filesystem.cli.client.internal.daemon;













class MultiplexingStream<StreamType>
{
  private ThreadLocal<StreamType> target = new ThreadLocal();
  private StreamType defaultTarget;
  
  MultiplexingStream(StreamType defaultTarget)
  {
    this.defaultTarget = defaultTarget;
  }
  

  StreamType findStream()
  {
    StreamType out = target.get();
    
    if (out == null) {
      return defaultTarget;
    }
    
    return out;
  }
  

  public void enroll(StreamType out)
  {
    if (target.get() != null) {
      throw new IllegalStateException();
    }
    
    target.set(out);
  }
  
  public void cancel()
  {
    if (target.get() == null) {
      throw new IllegalStateException();
    }
    
    target.remove();
  }
}
