package com.ibm.team.filesystem.cli.client.internal.debug;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.transport.HttpUtil.CharsetEncoding;
import com.ibm.team.repository.common.transport.HttpUtil.MediaType;
import com.ibm.team.repository.common.transport.TeamServiceException;
import com.ibm.team.repository.transport.client.ITeamRawRestServiceClient;
import com.ibm.team.repository.transport.client.ITeamRawRestServiceClient.IRawRestClientConnection;
import com.ibm.team.repository.transport.client.ITeamRawRestServiceClient.IRawRestClientConnection.Response;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.client.internal.WorkspaceManager;
import com.ibm.team.scm.common.IScmDebugRestService;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;













public abstract class DebugFetchCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final PositionalOptionDefinition OPTION_OUTPUT = new PositionalOptionDefinition("output", 1, 1);
  public static final PositionalOptionDefinition OPTION_ITEM_ID = new PositionalOptionDefinition("itemId", 1, 1);
  public static final PositionalOptionDefinition OPTION_STATE_ID = new PositionalOptionDefinition("stateId", 0, 1);
  public static final IOptionKey OPTION_DEEP = new OptionKey("deep");
  public static final IOptionKey OPTION_CONFIG = new OptionKey("config");
  public static final IOptionKey OPTION_HISTORY = new OptionKey("history");
  public static final String ARG_CONFIG = "config=true";
  public static final String ARG_DEEP = "deep=true";
  public static final String ARG_HISTORY = "history=true";
  
  public DebugFetchCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.login(config, config.getConnectionInfo());
    IClientLibraryContext context = ((WorkspaceManager)SCMPlatform.getWorkspaceManager(repo)).getContext();
    ICommandLine cli = config.getSubcommandCommandLine();
    
    try
    {
      ITeamRawRestServiceClient client = context.teamRepository().getRawRestServiceClient();
      URI uri = getURI(repo.getRepositoryURI(), cli);
      ITeamRawRestServiceClient.IRawRestClientConnection connection = client.getConnection(uri);
      connection.addRequestHeader("Accept", HttpUtil.MediaType.JSON.toString());
      connection.addRequestHeader("Accept", HttpUtil.MediaType.ANY.toString());
      connection.addRequestHeader("Accept-Charset", HttpUtil.CharsetEncoding.UTF8.toString());
      ITeamRawRestServiceClient.IRawRestClientConnection.Response response = connection.doGet();
      int code = response.getStatusCode();
      if (code != 200) {
        throw new FileSystemException(NLS.bind(Messages.DebugFetchCmd_BAD_RESPONSE, Integer.valueOf(code)));
      }
      
      dumpToFile(cli.getOption(OPTION_OUTPUT), response.getResponseStream());
    } catch (TeamServiceException e) {
      throw new FileSystemException(e);
    } catch (IOException e) {
      throw new FileSystemException(e);
    } catch (TeamRepositoryException e) {
      throw new FileSystemException(e);
    } catch (URISyntaxException e) {
      throw new FileSystemException(e);
    }
  }
  

  protected abstract String getType();
  

  private void dumpToFile(String path, InputStream input)
    throws IOException
  {
    File file = new File(path);
    if (file.exists())
      file.delete();
    if (file.exists())
      throw new IOException(NLS.bind(Messages.DebugFetchCmd_CANNOT_DELETE_FILE, file.getAbsolutePath()));
    OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
    transferStreams(new BufferedInputStream(input), output);
  }
  

  private static void transferStreams(InputStream source, OutputStream destination)
    throws IOException
  {
    try
    {
      RepoUtil.transfer(source, destination);
    }
    finally {
      try {
        destination.close();
      }
      catch (IOException localIOException1) {}
    }
  }
  


  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false);
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(OPTION_ITEM_ID, Messages.DebugFetchCmd_HELP_ITEM_ARG);
    options.addOption(OPTION_OUTPUT, Messages.DebugFetchCmd_HELP_OUTPUT_ARG);
    options.addOption(OPTION_STATE_ID, Messages.DebugFetchCmd_HELP_STATE_ARG);
    return options;
  }
  



  protected abstract List<String> getQueryArgs(ICommandLine paramICommandLine);
  



  protected URI getURI(String repoURI, ICommandLine cli)
    throws TeamRepositoryException
  {
    String type = getType();
    String item = cli.getOption(OPTION_ITEM_ID);
    String state = cli.getOption(OPTION_STATE_ID, null);
    StringBuffer buffer = new StringBuffer();
    
    buffer.append(repoURI);
    if (!repoURI.endsWith("/")) {
      buffer.append("/");
    }
    buffer.append("service");
    buffer.append("/");
    buffer.append(IScmDebugRestService.class.getName());
    
    buffer.append("/");
    buffer.append("fetch");
    
    buffer.append("/");
    buffer.append(type);
    
    buffer.append("/");
    buffer.append(item);
    
    if (state != null) {
      buffer.append("/");
      buffer.append(state);
    }
    

    List<String> query = getQueryArgs(cli);
    if ((query != null) && (!query.isEmpty())) {
      buffer.append("?");
      for (Iterator<String> iter = query.iterator(); iter.hasNext();) {
        buffer.append((String)iter.next());
        if (iter.hasNext()) {
          buffer.append(";");
        }
      }
    }
    try {
      return new URI(buffer.toString());
    } catch (URISyntaxException e) {
      throw new TeamRepositoryException(e);
    }
  }
}
