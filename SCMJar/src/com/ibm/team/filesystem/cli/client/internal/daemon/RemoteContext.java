package com.ibm.team.filesystem.cli.client.internal.daemon;

import com.ibm.team.filesystem.cli.minimal.protocol.Message;
import com.ibm.team.filesystem.cli.minimal.protocol.MessageType;
import com.ibm.team.filesystem.cli.minimal.protocol.ProtocolUtil;
import com.ibm.team.filesystem.cli.minimal.protocol.Reader;
import com.ibm.team.filesystem.client.internal.daemon.FSDaemon;
import com.ibm.team.rtc.cli.infrastructure.internal.core.AbstractExecutionContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;














public class RemoteContext
  extends AbstractExecutionContext
{
  final String appName;
  final FSDaemon daemon;
  final String[] arguments;
  final Map<String, String> env;
  final String cwd;
  private String[] terminatingError = null;
  
  private final InputStream in;
  
  private final OutputStream out;
  
  private final PrintStream stderr;
  
  private final PrintStream stdout;
  
  private final Charset charset;
  
  private InputStream stdin;
  
  public RemoteContext(long start, String appName, FSDaemon daemon, InputStream in, OutputStream responseStream, LinkedList<String> args, Map<String, String> env, String cwd)
  {
    this(start, appName, daemon, in, responseStream, args, env, cwd, Charset.forName("UTF-8"));
  }
  
  public RemoteContext(long start, String appName, FSDaemon daemon, InputStream in, OutputStream responseStream, LinkedList<String> args, Map<String, String> env, String cwd, Charset charset)
  {
    super(start);
    
    this.appName = appName;
    this.daemon = daemon;
    arguments = ((String[])args.toArray(new String[args.size()]));
    this.env = env;
    
    this.in = in;
    out = responseStream;
    
    this.cwd = cwd;
    
    stdout = new PrintStream(new RemoteOutputStream(this, responseStream, MessageType.STDOUT));
    stderr = new PrintStream(new RemoteOutputStream(this, responseStream, MessageType.STDERR));
    
    this.charset = charset;
  }
  
  public String getAppName() {
    return appName;
  }
  
  public String[] arguments() {
    return arguments;
  }
  
  public String readInput(String prompt, boolean mask) throws IOException {
    synchronized (this) {
      ProtocolUtil.writeMessage(out, MessageType.PASSWORD_READ, new byte[][] {
        prompt.getBytes("UTF-8"), Boolean.toString(mask).getBytes("UTF-8") });
      
      out.flush();
      
      Message msg = Reader.readMessage(in);
      if (msg.getMessageType() != MessageType.PASSWORD_RESPONSE) {
        throw new IOException("Unexpected message: " + msg.getMessageType());
      }
      
      if (ProtocolUtil.isCharsetUtf8(charset))
      {
        return new String(msg.getFields()[0], "UTF-8");
      }
      
      byte[] utf8Bytes = ProtocolUtil.transcodeToUtf8(msg.getFields()[0], charset);
      if ((utf8Bytes != null) && (utf8Bytes.length > 0))
      {
        return new String(utf8Bytes, "UTF-8");
      }
      
      return null;
    }
  }
  


  public void setTerminatingError(String title, String message)
  {
    boolean hasMessage = ((title != null) && (title.length() != 0)) || ((message != null) && (message.length() != 0));
    
    if (hasMessage) {
      terminatingError = new String[] { title, message };
    }
  }
  
  public PrintStream stderr() {
    return stderr;
  }
  
  public PrintStream stdout() {
    return stdout;
  }
  
  public InputStream stdin() {
    synchronized (this) {
      if (stdin == null) {
        stdin = new RemoteInputStream(this, out, in, charset);
      }
      
      return stdin;
    }
  }
  
  public String[] getTerminatingError() {
    return terminatingError;
  }
  
  public Map<String, String> environment() {
    return env;
  }
  
  public String getCurrentWorkingDirectory() {
    return cwd;
  }
  
  public boolean isLocal() {
    return false;
  }
  
  public Object getRemoteObject() {
    return daemon;
  }
}
