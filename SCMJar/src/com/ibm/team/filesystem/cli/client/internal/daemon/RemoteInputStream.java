package com.ibm.team.filesystem.cli.client.internal.daemon;

import com.ibm.team.filesystem.cli.minimal.protocol.Message;
import com.ibm.team.filesystem.cli.minimal.protocol.MessageType;
import com.ibm.team.filesystem.cli.minimal.protocol.ProtocolUtil;
import com.ibm.team.filesystem.cli.minimal.protocol.Reader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;












public class RemoteInputStream
  extends InputStream
{
  private final InputStream in;
  private final OutputStream out;
  private final Object lock;
  private final Charset charset;
  
  public RemoteInputStream(Object lock, OutputStream out, InputStream in)
  {
    this(lock, out, in, Charset.forName("UTF-8"));
  }
  
  public RemoteInputStream(Object lock, OutputStream out, InputStream in, Charset charset)
  {
    this.lock = lock;
    this.out = out;
    this.in = in;
    this.charset = charset;
  }
  
  public int read() throws IOException
  {
    synchronized (lock) {
      ProtocolUtil.writeMessage(out, MessageType.STDIN_READ, new byte[][] { "1".getBytes("UTF-8") });
      out.flush();
      
      Message msg = Reader.readMessage(in);
      if (msg == null) {
        throw new IOException("Unexpected front end termination");
      }
      
      if (msg.getMessageType() == MessageType.STDIN_CLOSED) {
        return -1;
      }
      
      if (msg.getMessageType() == MessageType.STDIN_RESPONSE) {
        if (msg.getFields()[0].length != 1) {
          throw new IOException(
            "Expected exactly one byte in response. Got \"" + 
            new String(msg.getFields()[0]) + "\" [" + 
            msg.getFields()[0].length + "]");
        }
        








        return msg.getFields()[0][0];
      }
      
      throw new IOException("Unexpected message " + msg);
    }
  }
}
