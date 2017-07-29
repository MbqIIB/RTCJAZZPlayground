package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.eclipse.osgi.util.NLS;

public class ListSnapshotsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public ListSnapshotsCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IScmCommandLineArgument selector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_SELECTOR, null), config);
    boolean isSnapshotName = cli.hasOption(OPT_NAME_FILTER);
    

    if ((isSnapshotName) && (selector == null)) {
      throw StatusHelper.argSyntax(Messages.ListSnapshotsCmd_SNAPSHOT_NAME_NOT_SPECIFIED);
    }
    
    if ((cli.hasOption(OPT_PROJECTAREA)) && (cli.hasOption(OPT_TEAMAREA))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, OPT_PROJECTAREA.getName(), OPT_TEAMAREA.getName()));
    }
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    IWorkspace ws = null;
    String ssPrefix = null;
    if ((!isSnapshotName) && (selector != null) && (selector.getItemSelector().length() > 0)) {
      SubcommandUtil.validateArgument(selector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      repo = RepoUtil.loginUrlArgAncestor(config, client, selector);
      ws = RepoUtil.getWorkspace(selector.getItemSelector(), true, true, repo, config);
    } else {
      repo = RepoUtil.loginUrlArgAnc(config, client);
      ssPrefix = selector != null ? selector.getItemSelector() : null;
    }
    
    IProcessArea processArea = null;
    if (cli.hasOption(OPT_PROJECTAREA)) {
      String projectAreaName = cli.getOption(OPT_PROJECTAREA);
      processArea = RepoUtil.getProjectArea(repo, projectAreaName, config);
    } else if (cli.hasOption(OPT_TEAMAREA)) {
      String teamAreaName = cli.getOption(OPT_TEAMAREA);
      processArea = RepoUtil.getTeamArea(teamAreaName, null, config, repo);
    }
    
    int maxResults = RepoUtil.getMaxResultsOption(cli);
    
    List<IBaselineSet> baselineSets = RepoUtil.getSnapshotByName(
      ws != null ? ws.getItemId().getUuidValue() : null, ssPrefix, processArea, false, 
      maxResults == Integer.MAX_VALUE ? maxResults : maxResults + 1, repo, config);
    

    Collections.sort(baselineSets, new Comparator() {
      public int compare(IBaselineSet b1, IBaselineSet b2) {
        return b1.getCreationDate().after(b2.getCreationDate()) ? -1 : 1;
      }
      
    });
    JSONArray snpshots = new JSONArray();
    int ssCount = jsonize(config, snpshots, baselineSets, cli, repo, maxResults);
    
    JSONObject jResult = new JSONObject();
    if (snpshots.size() > 0) {
      jResult.put("snapshots", snpshots);
    }
    
    printResults(jResult, config, ssCount, maxResults);
  }
  
  void printResults(JSONObject jResult, IScmClientConfiguration config, int ssCount, int maxResults)
  {
    if (config.isJSONEnabled())
    {
      config.getContext().stdout().print(jResult);
    }
    else {
      IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
      
      if (ssCount == 0) {
        printStream.println(Messages.ListSnapshotsCmd_NO_SNAPSHOTS);
        return;
      }
      
      ICommandLine cli = config.getSubcommandCommandLine();
      boolean verbose = cli.hasOption(CommonOptions.OPT_VERBOSE);
      String message = Messages.ListSnapshotsCmd_0;
      JSONArray jSnpShots = (JSONArray)jResult.get("snapshots");
      
      for (Object obj : jSnpShots)
      {
        JSONObject jSnapshot = (JSONObject)obj;
        
        String creationDate = (String)jSnapshot.get("creationDate");
        String comment = (String)jSnapshot.get("comment");
        
        if ((verbose) || (config.isJSONEnabled())) {
          message = Messages.ListSnapshotsCmd_3;
          
          if (comment.equals("<No comment>")) {
            message = Messages.ListSnapshotsCmd_4;
          }
        }
        
        printStream.println(NLS.bind(message, 
          new String[] { AliasUtil.selector((String)jSnapshot.get("name"), UUID.valueOf((String)jSnapshot.get("uuid")), 
          (String)jSnapshot.get("url"), RepoUtil.ItemType.SNAPSHOT), 
          creationDate, 
          comment }));
      }
      

      if ((!cli.hasOption(CommonOptions.OPT_MAXRESULTS)) && (ssCount >= maxResults)) {
        config.getContext().stdout().println(NLS.bind(Messages.ListCmd_MORE_ITEMS_AVAILABLE, CommonOptions.OPT_MAXRESULTS.getName()));
      }
    }
  }
  
  int jsonize(IScmClientConfiguration config, JSONArray jSnapshots, List<IBaselineSet> snapshots, ICommandLine cli, ITeamRepository repo, int maxResults)
    throws FileSystemException
  {
    SimpleDateFormat format = (SimpleDateFormat)DateFormat.getDateTimeInstance(2, 3);
    format = SubcommandUtil.getDateFormat(format.toPattern(), config);
    boolean verbose = cli.hasOption(CommonOptions.OPT_VERBOSE);
    int ssCount = 0;
    
    for (IBaselineSet snapshot : snapshots)
    {
      if (ssCount >= maxResults)
        break;
      JSONObject obj = JSONPrintUtil.jsonize(snapshot.getName(), snapshot.getItemId().getUuidValue(), repo.getRepositoryURI());
      
      String comment = null;
      if ((verbose) || (config.isJSONEnabled())) {
        comment = snapshot.getComment();
        if (comment.length() == 0) {
          comment = "<No comment>";
        }
        obj.put("comment", comment);
      }
      obj.put("creationDate", format.format(snapshot.getCreationDate()));
      jSnapshots.add(obj);
      




      ssCount++;
    }
    
    return ssCount;
  }
  

  public static final PositionalOptionDefinition OPT_SELECTOR = new PositionalOptionDefinition("selector", 0, 1, "@");
  public static final NamedOptionDefinition OPT_PROJECTAREA = new NamedOptionDefinition(null, "projectarea", 1, "@");
  public static final NamedOptionDefinition OPT_TEAMAREA = new NamedOptionDefinition(null, "teamarea", 1, "@");
  public static final IOptionKey OPT_NAME_FILTER = new OptionKey("name");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.ListSnapshotsCmd_2)
      .addOption(CommonOptions.OPT_MAXRESULTS, CommonOptions.OPT_MAXRESULTS_HELP)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_PROJECTAREA, Messages.ListCmdOptions_PROJECTAREA, false)
      .addOption(OPT_TEAMAREA, Messages.ListCmdOptions_TEAMAREA, false))
      .addOption(OPT_NAME_FILTER, "n", "name", Messages.ListSnapShotCmdOptions_NAME_FILTER, 0)
      .addOption(OPT_SELECTOR, NLS.bind(Messages.ListSnapshotsCmd_5, ((OptionKey)OPT_NAME_FILTER).getName()));
    
    return options;
  }
}
