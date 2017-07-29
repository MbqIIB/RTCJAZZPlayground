package com.ibm.team.filesystem.cli.client.internal.querycommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import org.eclipse.osgi.util.NLS;





public class QueryCmd
  extends AbstractSubcommand
{
  public QueryCmd() {}
  
  public static enum VersionMode
  {
    HIDE,  SHORT_VERSION,  LONG_VERSION;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    int count = 0;
    if (cli.hasOption(QueryOptions.OPT_STREAM)) count++;
    if (cli.hasOption(QueryOptions.OPT_WORKSPACE)) count++;
    if (cli.hasOption(QueryOptions.OPT_SNAPSHOT)) count++;
    if (cli.hasOption(QueryOptions.OPT_BASELINE)) count++;
    if (cli.hasOption(QueryOptions.OPT_COMPONENT)) count++;
    if (cli.hasOption(QueryOptions.OPT_FILES)) { count++;
    }
    if (count > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_6_ARGUMENTS, 
        new String[] { QueryOptions.OPT_WORKSPACE.getName(), 
        QueryOptions.OPT_STREAM.getName(), 
        QueryOptions.OPT_SNAPSHOT.getName(), 
        QueryOptions.OPT_BASELINE.getName(), 
        QueryOptions.OPT_COMPONENT.getName(), 
        QueryOptions.OPT_FILES.getName() }));
    }
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAnc(config, client);
    
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
      .getServiceInterface(IScmRichClientRestService.class);
    
    String queryString = cli.getOption(QueryOptions.OPT_QUERY_STR);
    
    if (cli.hasOption(QueryOptions.OPT_STREAM)) {
      int maxResults = RepoUtil.getMaxResultsOption(cli);
      
      StreamQueryCmd.queryStream(queryString, repo, maxResults, cli.hasOption(CommonOptions.OPT_MAXRESULTS), scmService, config, client);
    }
    else if (cli.hasOption(QueryOptions.OPT_WORKSPACE)) {
      int maxResults = RepoUtil.getMaxResultsOption(cli);
      
      WorkspaceQueryCmd.queryWorkspace(queryString, repo, maxResults, cli.hasOption(CommonOptions.OPT_MAXRESULTS), scmService, config, client);
    }
    else if (cli.hasOption(QueryOptions.OPT_BASELINE)) {
      int maxResults = RepoUtil.getMaxResultsOption(cli);
      
      BaselineQueryCmd.queryBaseline(queryString, repo, maxResults, cli.hasOption(CommonOptions.OPT_MAXRESULTS), scmService, config, client);
    }
    else if (cli.hasOption(QueryOptions.OPT_SNAPSHOT)) {
      int maxResults = RepoUtil.getMaxResultsOption(cli);
      
      SnapshotQueryCmd.querySnapshot(queryString, repo, maxResults, cli.hasOption(CommonOptions.OPT_MAXRESULTS), scmService, config, client);
    }
    else if (cli.hasOption(QueryOptions.OPT_COMPONENT)) {
      int maxResults = RepoUtil.getMaxResultsOption(cli);
      
      ComponentQueryCmd.queryComponent(queryString, repo, maxResults, cli.hasOption(CommonOptions.OPT_MAXRESULTS), scmService, config, client);
    }
    else {
      VersionMode vMode = VersionMode.HIDE;
      
      if ((cli.hasOption(QueryOptions.OPT_SHOW_SHORT_VERSION_ID)) && 
        (cli.hasOption(QueryOptions.OPT_SHOW_FULL_VERSION_ID)))
      {
        throw StatusHelper.argSyntax(NLS.bind(
          Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
          cli.getDefinition().getOption(QueryOptions.OPT_SHOW_SHORT_VERSION_ID).getName(), 
          cli.getDefinition().getOption(QueryOptions.OPT_SHOW_FULL_VERSION_ID).getName()));
      }
      
      if (cli.hasOption(QueryOptions.OPT_SHOW_SHORT_VERSION_ID)) {
        vMode = VersionMode.SHORT_VERSION;
      } else if (cli.hasOption(QueryOptions.OPT_SHOW_FULL_VERSION_ID)) {
        vMode = VersionMode.LONG_VERSION;
      }
      

      VersionableQueryCmd.queryVersionables(queryString, repo, scmService, vMode, config);
    }
  }
}
