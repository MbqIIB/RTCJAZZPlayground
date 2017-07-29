package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentTarget;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceFlowEntryDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;






public class FlowComponentTargetCmd
  extends FlowWorkspaceTargetCmd
{
  public FlowComponentTargetCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    init(config, client);
    

    IScmCommandLineArgument targetSelector = ScmCommandLineArgument.create(cli.getOptionValue(FlowComponentTargetCmdOptions.OPT_COMPONENT), config);
    setComponentFlowTarget(ws, targetWs, targetSelector, client, config);
  }
  
  public static void setComponentFlowTarget(ParmsWorkspace ws, ParmsWorkspace targetWs, IScmCommandLineArgument targetSelector, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    SubcommandUtil.validateArgument(targetSelector, RepoUtil.ItemType.COMPONENT);
    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    ITeamRepository repo = RepoUtil.getSharedRepository(repositoryUrl, true);
    RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, Collections.singletonList(targetSelector), repo, config);
    
    WorkspaceComponentDTO compDTO = RepoUtil.getComponent(wsDetails, targetSelector.getItemSelector(), client, config);
    
    if (config.isDryRun()) {
      return;
    }
    
    if (!canSetComponentFlowTarget(wsDetails, compDTO.getItemId()))
    {
      throw StatusHelper.disallowed(Messages.FlowTargetCmd_CHANGE_FAILED);
    }
    

    ParmsComponentTarget parms = new ParmsComponentTarget();
    activeWorkspace = ws;
    targetWorkspace = targetWs;
    activeComponentItemIds = new String[] { compDTO.getItemId() };
    try
    {
      client.postSetComponentTarget(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(
        Messages.FlowTargetCmd_CHANGE_FAILED, e, new IndentingPrintStream(config.getContext().stderr()), targetWorkspace.repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.FlowTargetCmd_TARGET_SUCCESFULLY_CHANGED);
  }
  
  private static boolean canSetComponentFlowTarget(WorkspaceDetailsDTO wsDetails, String componentId) {
    List<String> incomingComponentScopes = null;
    List<String> outgoingComponentScopes = null;
    List<WorkspaceFlowEntryDTO> wsFlowList = wsDetails.getFlowEntries();
    for (WorkspaceFlowEntryDTO flowEntryDTO : wsFlowList) {
      if (flowEntryDTO.isCurrentIncomingFlow()) {
        incomingComponentScopes = flowEntryDTO.getScopedComponentItemIds();
      }
      if (flowEntryDTO.isCurrentOutgoingFlow()) {
        outgoingComponentScopes = flowEntryDTO.getScopedComponentItemIds();
      }
    }
    

    if ((!isComponentScoped(incomingComponentScopes, componentId)) || 
      (!isComponentScoped(outgoingComponentScopes, componentId))) {
      return false;
    }
    return true;
  }
  
  private static boolean isComponentScoped(List<String> componentScopes, String componentId) {
    if ((componentScopes == null) || (componentScopes.size() == 0)) {
      return true;
    }
    
    for (String compId : componentScopes) {
      if (compId.equals(componentId)) {
        return true;
      }
    }
    return false;
  }
}
