package com.ibm.team.filesystem.cli.client.internal.daemon;

import com.ibm.team.filesystem.cli.minimal.protocol.MessageType;
import com.ibm.team.filesystem.cli.minimal.protocol.ProtocolUtil;
import java.io.IOException;
import java.io.OutputStream;








public class RemoteOutputStream
  extends OutputStream
{
  private final OutputStream out;
  private final MessageType message;
  private final Object lock;
  
  public RemoteOutputStream(Object lock, OutputStream out, MessageType message)
  {
    this.lock = lock;
    this.out = out;
    this.message = message;
  }
  
  public void write(int b) throws IOException
  {
    synchronized (lock) {
      try {
        byte[] bytes = { (byte)b };
        ProtocolUtil.writeMessage(out, message, new byte[][] { bytes });
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
  
  public void write(byte[] b, int off, int len) throws IOException
  {
    synchronized (lock) {
      try {
        byte[] toWrite = null;
        
        if ((off == 0) && (len == b.length)) {
          toWrite = b;
        } else {
          toWrite = new byte[len];
          System.arraycopy(b, off, toWrite, 0, len);
        }
        
        ProtocolUtil.writeMessage(out, message, new byte[][] { toWrite });
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
