package com.ibm.team.filesystem.cli.client.internal.daemon;

import java.io.IOException;
import java.io.InputStream;










public class MultiplexingInputStream
  extends InputStream
{
  private final MultiplexingStream<InputStream> multiplexer;
  
  MultiplexingInputStream(InputStream defaultIn)
  {
    multiplexer = new MultiplexingStream(defaultIn);
  }
  
  public int read() throws IOException
  {
    return ((InputStream)multiplexer.findStream()).read();
  }
  
  public void enroll(InputStream in) {
    multiplexer.enroll(in);
  }
  
  public void close() throws IOException
  {
    ((InputStream)multiplexer.findStream()).close();
  }
  
  public void cancel() {
    multiplexer.cancel();
  }
}
