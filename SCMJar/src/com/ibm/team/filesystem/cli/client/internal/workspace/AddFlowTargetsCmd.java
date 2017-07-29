package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsFlowTargetChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;



public class AddFlowTargetsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public AddFlowTargetsCmd() {}
  
  public static final PositionalOptionDefinition OPT_TARGETS = new PositionalOptionDefinition("target-workspace", 1, -1, "@");
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.FlowTargetCmdOptions_WORKSPACE_HELP)
      .addOption(OPT_TARGETS, Messages.AddFlowTargetsCmdOptions_TARGET_HELP);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    IScmCommandLineArgument srcSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(srcSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, srcSelector);
    IWorkspace wsFound = RepoUtil.getWorkspace(srcSelector.getItemSelector(), true, true, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    

    List<IScmCommandLineArgument> targetSelectorList = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_TARGETS), config);
    SubcommandUtil.validateArgument(targetSelectorList, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    List<ParmsWorkspace> targetWsList = new ArrayList();
    IWorkspace targetWs; for (IScmCommandLineArgument targetSelector : targetSelectorList) {
      ITeamRepository targetRepo = RepoUtil.loginUrlArgAncestor(config, client, targetSelector);
      targetWs = RepoUtil.getWorkspace(targetSelector.getItemSelector(), true, true, targetRepo, config);
      targetWsList.add(new ParmsWorkspace(targetRepo.getRepositoryURI(), targetWs.getItemId().getUuidValue()));
    }
    
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    workspace = ws;
    flowTargets = new ParmsFlowTargetChange[targetWsList.size()];
    
    int count = 0;
    for (ParmsWorkspace targetWs : targetWsList) {
      ParmsFlowTargetChange flowTargetChange = new ParmsFlowTargetChange();
      workspace = targetWs;
      flowTargets[(count++)] = flowTargetChange;
    }
    try
    {
      client.postPutWorkspace(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.AddFlowTargetsCmd_FAILURE, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    
    config.getContext().stdout().println(Messages.AddFlowTargetsCmd_SUCCESS);
  }
}
