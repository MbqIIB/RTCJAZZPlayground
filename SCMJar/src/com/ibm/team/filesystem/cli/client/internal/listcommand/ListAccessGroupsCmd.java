package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.ConnectionInfo;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.process.client.IAccessGroupClientService;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.PrintStream;
import java.util.Arrays;





public class ListAccessGroupsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListAccessGroupsCmd() {}
  
  public Options getOptions()
    throws ConflictingOptionException
  {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, CommonOptions.OPT_VERBOSE_HELP)
      .addOption(ListWorkspacesOptions.OPT_NAME_FILTER, "n", "name", Messages.ListCmdOptions_NAME_FILTER, 1)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP);
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    ConnectionInfo connection = config.getConnectionInfo();
    ITeamRepository repo = RepoUtil.login(config, client, connection);
    
    String nameSelector = cli.getOption(ListWorkspacesOptions.OPT_NAME_FILTER, null);
    int maxResults = RepoUtil.getMaxResultsOption(cli);
    boolean verbose = cli.hasOption(CommonOptions.OPT_VERBOSE);
    
    IAccessGroupClientService accessGroupService = (IAccessGroupClientService)repo.getClientLibrary(IAccessGroupClientService.class);
    try
    {
      IAccessGroup[] accessGroups = accessGroupService.getAccessGroups(nameSelector, null);
      if (accessGroups.length > maxResults) {
        accessGroups = (IAccessGroup[])Arrays.copyOfRange(accessGroups, 0, maxResults);
      }
      printAccessGroups(accessGroups, repo, client, config, verbose);
    } catch (TeamRepositoryException e) {
      throw new FileSystemException(e);
    }
  }
  
  private void printAccessGroups(IAccessGroup[] accessGroups, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config, boolean verbose) {
    JSONArray jAccessGroups = new JSONArray();
    for (IAccessGroup accessGroup : accessGroups)
    {
      JSONObject jAccessGroup = JSONPrintUtil.jsonize(accessGroup.getName(), accessGroup.getGroupContextId().getUuidValue(), repo.getRepositoryURI());
      jAccessGroup.put("desc", accessGroup.getDescription());
      jAccessGroups.add(jAccessGroup);
    }
    
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(jAccessGroups);
      return;
    }
    
    if (jAccessGroups.size() == 0) {
      return;
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    for (Object obj : jAccessGroups) {
      JSONObject jAccessGroup = (JSONObject)obj;
      
      out.println(AliasUtil.selector(config.getAliasConfig(), (String)jAccessGroup.get("name"), 
        UUID.valueOf((String)jAccessGroup.get("uuid")), (String)jAccessGroup.get("url"), RepoUtil.ItemType.ACCESSGROUP));
      
      if (verbose) {
        String desc = (String)jAccessGroup.get("desc");
        if ((desc != null) && (!desc.trim().isEmpty())) {
          String[] lines = desc.split("\n");
          int i = 0;
          do { String line = lines[i].trim();
            if (!line.isEmpty())
            {

              out.indent().println(SubcommandUtil.sanitizeText(lines[i].trim(), 80, true));
            }
            i++; if (i >= lines.length) break; } while (i < 5);


        }
        else
        {


          out.indent().println(Messages.ListAccessGroups_NO_DESCRIPTION);
        }
      }
    }
  }
}
