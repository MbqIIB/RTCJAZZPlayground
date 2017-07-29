package com.ibm.team.filesystem.cli.client.internal.daemon;

import com.ibm.team.filesystem.cli.minimal.protocol.Message;
import com.ibm.team.filesystem.cli.minimal.protocol.MessageType;
import com.ibm.team.filesystem.cli.minimal.protocol.ProtocolUtil;
import com.ibm.team.filesystem.cli.minimal.protocol.Reader;
import com.ibm.team.filesystem.client.internal.daemon.FSDaemon;
import com.ibm.team.filesystem.client.internal.http.HttpContext;
import com.ibm.team.filesystem.client.internal.http.HttpRequest;
import com.ibm.team.filesystem.client.internal.http.HttpResponse;
import com.ibm.team.filesystem.client.internal.http.HttpServer;
import com.ibm.team.filesystem.client.internal.http.IExternalManager;
import com.ibm.team.filesystem.client.internal.http.ProtocolSwitchingHttpHandler;
import com.ibm.team.rtc.cli.infrastructure.internal.core.SubcommandLauncher;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

















public class CommandLineClient
  extends ProtocolSwitchingHttpHandler
  implements IExternalManager
{
  private static final String GROUP_FEC = "FrontEndClient";
  private FSDaemon daemon;
  
  public CommandLineClient() {}
  
  public boolean willManage(HttpRequest request, HttpResponse response)
  {
    return true;
  }
  
  protected String getProtocol()
  {
    return "vnd.ibm.jazz.cli.minimal-1.0";
  }
  
  private static Logger logger = Logger.getLogger(CommandLineClient.class.getName());
  



  protected void handleRequest(HttpRequest request, HttpResponse response)
    throws IOException
  {
    long start = System.currentTimeMillis();
    
    String charsetName = "UTF-8";
    Charset charset = Charset.forName(charsetName);
    List<String> charsetNames = request.getHeader("Accept-Charset");
    if ((charsetNames != null) && (charsetNames.size() > 0))
    {
      logger.info("Accept Charset received: " + charsetNames.toString());
      if (Charset.isSupported((String)charsetNames.get(0)))
      {
        logger.info("Charset is supported by this JRE");
        charsetName = (String)charsetNames.get(0);
        charset = Charset.forName(charsetName);
      }
      else
      {
        logger.warning("Charset received is not supported; defaulting to UTF-8");
      }
    }
    
    logger.info("charset = " + charsetName);
    
    InputStream in = response.getServerInputStream();
    

    String appName = null;
    LinkedList<String> args = new LinkedList();
    Map<String, String> env = new HashMap();
    String cwd = null;
    Message msg;
    for (;;) {
      msg = Reader.readMessage(in);
      
      switch (msg.getMessageType()) {
      case APP_NAME: 
        if (ProtocolUtil.isCharsetUtf8(charset))
        {
          appName = new String(msg.getFields()[0], "UTF-8");
        }
        else
        {
          appName = new String(ProtocolUtil.transcodeToUtf8(msg.getFields()[0], charset), 
            "UTF-8");
        }
        break;
      
      case ARGUMENT: 
        if (ProtocolUtil.isCharsetUtf8(charset))
        {
          args.add(new String(msg.getFields()[0], "UTF-8"));
        }
        else
        {
          args.add(new String(ProtocolUtil.transcodeToUtf8(msg.getFields()[0], charset), 
            "UTF-8"));
        }
        break;
      
      case CWD: 
        if (ProtocolUtil.isCharsetUtf8(charset))
        {
          env.put(new String(msg.getFields()[0], "UTF-8"), 
            new String(msg.getFields()[1], "UTF-8"));
        }
        else
        {
          env.put(new String(ProtocolUtil.transcodeToUtf8(msg.getFields()[0], charset), 
            "UTF-8"), 
            new String(ProtocolUtil.transcodeToUtf8(msg.getFields()[1], charset), 
            "UTF-8"));
        }
        break;
      
      case STDOUT: 
        if (ProtocolUtil.isCharsetUtf8(charset))
        {
          cwd = new String(msg.getFields()[0], "UTF-8");
        }
        else
        {
          cwd = new String(ProtocolUtil.transcodeToUtf8(msg.getFields()[0], charset), 
            "UTF-8");
        }
        
        break;
      }
      
    }
    break label552;
    throw new IOException("Unexpected message: " + msg.getMessageType());
    


    label552:
    

    RemoteContext ctx = new RemoteContext(start, appName, daemon, in, response.getResponseStream(), args, env, cwd, charset);
    

    SubcommandLauncher runner = new SubcommandLauncher("FrontEndClient", ctx.stderr());
    
    int result = -1;
    try
    {
      try {
        if (cwd == null) {
          ctx.stderr().print(new RuntimeException("CWD not defined"));
        }
        











        for (;;)
        {
          if (ctx.getTerminatingError() != null) {
            ctx.stderr().println(ctx.getTerminatingError()[0] + ":");
            ctx.stderr().println(ctx.getTerminatingError()[1]);
          }
          



          ctx.stdout().flush();
          ctx.stderr().flush();
          

          ProtocolUtil.writeMessage(response.getResponseStream(), MessageType.EXIT_CODE, new byte[][] { { (byte)result } });return;
          if (appName != null) break;
          ctx.stderr().print(new RuntimeException(MessageType.APP_NAME + " not defined"));
        }
        


        result = runner.run(ctx, null, null);
      } catch (Exception e) {
        ctx.stderr().print(e.toString());
        result = -13;
      }
      finally {
        if (ctx.getTerminatingError() != null) {
          ctx.stderr().println(ctx.getTerminatingError()[0] + ":");
          ctx.stderr().println(ctx.getTerminatingError()[1]);
        }
      }
    }
    finally
    {
      ctx.stdout().flush();
      ctx.stderr().flush();
      

      ProtocolUtil.writeMessage(response.getResponseStream(), MessageType.EXIT_CODE, new byte[][] { { (byte)result } });
      in.close();
      response.getResponseStream().close();
      



      System.gc();
    }
    ctx.stdout().flush();
    ctx.stderr().flush();
    

    ProtocolUtil.writeMessage(response.getResponseStream(), MessageType.EXIT_CODE, new byte[][] { { (byte)result } });
    in.close();
    response.getResponseStream().close();
    



    System.gc();
  }
  


  public void registered(FSDaemon fsd, HttpServer httpServer, Collection<HttpContext> contexts)
  {
    daemon = fsd;
  }
  
  public void remoteClosed() {}
  
  public void shutdown() {}
}
