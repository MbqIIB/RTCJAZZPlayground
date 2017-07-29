package com.ibm.team.filesystem.cli.client.internal.subcommands;

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
import com.ibm.team.filesystem.client.rest.parameters.ParmsGetWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsGetWorkspaceDetails;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceFlowEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.GetWorkspaceDetailsErrorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.GetWorkspaceDetailsResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;




public class FlowWorkspaceTargetUnsetCmd
  extends AbstractSubcommand
{
  public FlowWorkspaceTargetUnsetCmd() {}
  
  protected List<ParmsWorkspace> init(IScmClientConfiguration config, IFilesystemRestClient client, ParmsWorkspace pw1)
    throws FileSystemException
  {
    ParmsGetWorkspace parm = new ParmsGetWorkspace();
    workspace = pw1;
    includeFlowTargets = Boolean.valueOf(true);
    
    ParmsGetWorkspaceDetails parms = new ParmsGetWorkspaceDetails();
    workspaces = new ParmsGetWorkspace[] { parm };
    GetWorkspaceDetailsResultDTO result = null;
    try {
      result = client.getWorkspaceDetails(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.FlowTargetUnsetCmd_WORKSPACE_DETAILS_NOT_FOUND, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    

    if (result.getErrors().size() > 0) {
      GetWorkspaceDetailsErrorDTO error = (GetWorkspaceDetailsErrorDTO)result.getErrors().get(0);
      throw StatusHelper.failure(error.getMessage(), null);
    }
    
    if (result.getWorkspaceDetails().size() != 1) {
      throw StatusHelper.failure(Messages.FlowTargetUnsetCmd_WORKSPACE_NOT_FOUND, null);
    }
    
    WorkspaceDetailsDTO workspace = (WorkspaceDetailsDTO)result.getWorkspaceDetails().get(0);
    
    if (workspace.getFlowEntries().size() == 0) {
      throw StatusHelper.failure(Messages.FlowTargetUnsetCmd_NOTHING_TO_UNSET, null);
    }
    
    List<ParmsWorkspace> list = new ArrayList();
    for (WorkspaceFlowEntryDTO flowEntry : workspace.getFlowEntries())
    {
      if ((flowEntry.isCurrentIncomingFlow()) || (flowEntry.isCurrentOutgoingFlow())) {
        ParmsWorkspace targetWS = new ParmsWorkspace();
        
        repositoryUrl = flowEntry.getRepositoryURL();
        workspaceItemId = flowEntry.getWorkspaceItemId();
        list.add(targetWS);
      }
    }
    
    if (list.size() == 0) {
      throw StatusHelper.failure(Messages.FlowTargetUnsetCmd_NOTHING_TO_UNSET, null);
    }
    
    return list;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    

    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    
    IWorkspace ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    
    ParmsWorkspace src = new ParmsWorkspace();
    repositoryUrl = repo.getRepositoryURI();
    workspaceItemId = ws.getItemId().getUuidValue();
    List<ParmsWorkspace> flowTargets = init(config, client, src);
    
    if (config.isDryRun()) {
      return;
    }
    

    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    workspace = src;
    flowTargetsToRemove = new ParmsWorkspace[flowTargets.size()];
    flowTargets.toArray(flowTargetsToRemove);
    try
    {
      client.postPutWorkspace(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.FlowTargetUnsetCmd_UNSET_FAILED, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.FlowTargetUnsetCmd_TARGET_SUCCESSFULLY_UNSET);
  }
}
