package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaseline;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.PrintStream;
import java.util.List;


public class BaselineHierarchyCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  private IFilesystemRestClient client;
  
  public BaselineHierarchyCmd() {}
  
  static final PositionalOptionDefinition OPT_BASELINES_SELECTOR = new PositionalOptionDefinition("baseline", 0, 1);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(OPT_BASELINES_SELECTOR, Messages.BaselineHierarchyCmdOptions_BASELINE_HIERARCHY_TO_SHOW);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    IUuidAliasRegistry.IUuidAlias uuid = null;
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    if (cli.hasOption(OPT_BASELINES_SELECTOR)) {
      List<String> baselineSelectors = cli.getOptions(OPT_BASELINES_SELECTOR);
      baselineSelectors.size();
      


      for (String selector : baselineSelectors) {
        uuid = RepoUtil.lookupUuidAndAlias(selector);
      }
    }
    
    if (uuid == null)
    {
      return;
    }
    
    client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAnc(config, client);
    
    ParmsBaseline parms = new ParmsBaseline();
    baselineItemId = uuid.getUuid().getUuidValue();
    repositoryUrl = repo.getRepositoryURI();
    
    try
    {
      BaselineDTO baselineDTO = client.postBaselineHierarchy(parms, null);
      printResult(repo, baselineDTO);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.BaselineHierarchyCmd_COULD_NOT_DISPLAY_HIERARCHY, e, new IndentingPrintStream(config.getContext().stderr()));
    }
  }
  
  private void printResult(ITeamRepository repo, BaselineDTO baselineDTO) throws FileSystemException {
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(5);
    options.enablePrinter(39);
    
    JSONObject baseline = JSONPrintUtil.jsonizeBaseline(baselineDTO, options);
    if (config.isJSONEnabled()) {
      config.getContext().stdout().print(baseline);
    } else {
      IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
      
      PendingChangesUtil.printBaseline(baseline, repo.getRepositoryURI(), options, printStream);
    }
  }
}
