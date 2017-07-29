package com.ibm.team.filesystem.cli.client.internal.daemon;

import java.io.IOException;
import java.io.OutputStream;










public class MultiplexingOutputStream
  extends OutputStream
{
  private final MultiplexingStream<OutputStream> multiplexer;
  
  MultiplexingOutputStream(OutputStream defaultOut)
  {
    multiplexer = new MultiplexingStream(defaultOut);
  }
  
  public void write(int b) throws IOException
  {
    ((OutputStream)multiplexer.findStream()).write(b);
  }
  
  public void write(byte[] b, int off, int len) throws IOException
  {
    try {
      byte[] toWrite = null;
      
      if ((off == 0) && (len == b.length)) {
        toWrite = b;
      } else {
        toWrite = new byte[len];
        System.arraycopy(b, off, toWrite, 0, len);
      }
      
      ((OutputStream)multiplexer.findStream()).write(toWrite, 0, len);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public void close()
    throws IOException
  {
    ((OutputStream)multiplexer.findStream()).close();
  }
  
  public void enroll(OutputStream out) {
    multiplexer.enroll(out);
  }
  
  public void cancel() throws IOException {
    multiplexer.cancel();
  }
}
