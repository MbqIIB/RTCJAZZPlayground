package com.ibm.team.filesystem.cli.client.internal.createcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsFlowTargetChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPostWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsReadScope;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceReplaceWithSnapshot;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdate;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdateDilemmaHandler;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.WorkspaceUpdateResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ReadScopeDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceFlowEntryDTO;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IAuditableHandle;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.ActiveChangeSetsException;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IWorkspace;
import java.util.Collections;
import java.util.List;
import org.eclipse.osgi.util.NLS;











public class CreateStreamCmd
  extends AbstractSubcommand
{
  public CreateStreamCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    validateArguments(subargs);
    
    config.setEnableJSON(subargs.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(subargs.getOptionValue(CreateWorkspaceBaseOptions.OPT_NAME), config);
    
    IScmCommandLineArgument flowToStream = ScmCommandLineArgument.create(subargs.getOptionValue(CreateWorkspaceBaseOptions.OPT_STREAM, null), config);
    SubcommandUtil.validateArgument(flowToStream, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    IScmCommandLineArgument snapshot = ScmCommandLineArgument.create(subargs.getOptionValue(CreateWorkspaceBaseOptions.OPT_SNAPSHOT, null), config);
    SubcommandUtil.validateArgument(snapshot, RepoUtil.ItemType.SNAPSHOT);
    IScmCommandLineArgument duplicateWsArg = ScmCommandLineArgument.create(subargs.getOptionValue(CreateWorkspaceBaseOptions.OPT_DUPLICATE, null), config);
    SubcommandUtil.validateArgument(duplicateWsArg, isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE);
    
    IScmCommandLineArgument autoFileLockPatternsArg = ScmCommandLineArgument.create(subargs.getOptionValue(CreateStreamOptions.OPT_AUTOFILELOCKPATTERNS, null), config);
    

    ITeamRepository streamOrSnapshotRepo = null;ITeamRepository duplicateWsRepo = null;
    ParmsWorkspace stream = null;ParmsWorkspace duplicateWs = null;
    IBaselineSet snap = null;
    
    if (flowToStream != null) {
      streamOrSnapshotRepo = RepoUtil.loginUrlArgAncestor(config, client, flowToStream);
      IWorkspace streamFound = RepoUtil.getWorkspace(flowToStream.getItemSelector(), true, true, 
        streamOrSnapshotRepo, config);
      stream = new ParmsWorkspace(streamOrSnapshotRepo.getRepositoryURI(), streamFound.getItemId().getUuidValue());
    } else if (snapshot != null) {
      streamOrSnapshotRepo = RepoUtil.loginUrlArgAncestor(config, client, snapshot);
      snap = RepoUtil.getSnapshot(null, snapshot.getItemSelector(), streamOrSnapshotRepo, config);
    } else if (duplicateWsArg != null) {
      duplicateWsRepo = RepoUtil.loginUrlArgAncestor(config, client, duplicateWsArg);
      IWorkspace wsFound = RepoUtil.getWorkspace(duplicateWsArg.getItemSelector(), !isStream(), isStream(), 
        duplicateWsRepo, config);
      duplicateWs = new ParmsWorkspace(duplicateWsRepo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    }
    
    ITeamRepository repo = null;
    try {
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    } catch (FileSystemException e) {
      if (streamOrSnapshotRepo != null) {
        repo = streamOrSnapshotRepo;
      } else if (duplicateWsRepo != null) {
        repo = duplicateWsRepo;
      } else {
        throw e;
      }
    }
    

    ParmsPostWorkspace parms = new ParmsPostWorkspace();
    name = wsSelector.getItemSelector();
    isStream = Boolean.valueOf(isStream());
    repositoryUrl = repo.getRepositoryURI();
    
    if ((isStream()) && (autoFileLockPatternsArg != null)) {
      autoFileLockPatterns = autoFileLockPatternsArg.getStringValue();
    }
    
    WorkspaceDetailsDTO duplicateWsDetails = null;
    if (subargs.hasOption(CreateWorkspaceBaseOptions.OPT_DUPLICATE)) {
      duplicateWsDetails = 
        (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(duplicateWs), client, config).get(0);
      setDuplicateParams(parms, duplicateWsDetails, client, subargs);
    } else {
      setParams(parms, stream, repo, client, subargs);
    }
    

    try
    {
      wsDetails = client.postWorkspace(parms, null);
    } catch (ActiveChangeSetsException e) { WorkspaceDetailsDTO wsDetails;
      if ((e.getData() instanceof IChangeSetHandle))
      {
        IChangeSetHandle csHandle = (IChangeSetHandle)e.getData();
        String compName;
        try {
          IChangeSet cs = (IChangeSet)repo.itemManager().fetchCompleteItem(csHandle, 0, null);
          IComponent comp = (IComponent)repo.itemManager().fetchCompleteItem(cs.getComponent(), 0, null);
          
          compName = comp.getName();
        } catch (TeamRepositoryException localTeamRepositoryException1) { String compName;
          compName = null;
        }
        
        if (compName != null)
        {
          throw StatusHelper.inappropriateArgument(NLS.bind(getActiveChangeSetErrorMessageWithComponentName(), compName));
        }
      }
      
      throw StatusHelper.inappropriateArgument(getActiveChangeSetErrorMessageNoName());
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(getCreationFailureMsg(), e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    WorkspaceDetailsDTO wsDetails;
    if ((duplicateWsDetails != null) && (duplicateWsDetails.getFlowEntries().size() > 0)) {
      updateFlowTargets(wsDetails, duplicateWsDetails, repo, client);
    }
    

    if (snap != null) {
      updateWithSnapshotInfo(wsDetails, snap, streamOrSnapshotRepo, repo, client);
    }
    
    JSONObject wsObj = JSONPrintUtil.jsonizeResult(getSuccessfulCreationMsg(), wsDetails.getName(), 
      wsDetails.getItemId(), wsDetails.getRepositoryURL(), isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE);
    
    if ((flowToStream == null) && (snap == null) && (duplicateWs == null)) {
      createComponent(wsDetails, client, config, subargs, wsObj);
    }
    
    PendingChangesUtil.printSuccess(wsObj, config);
  }
  
  private void setDuplicateParams(ParmsPostWorkspace parms, WorkspaceDetailsDTO wsDetails, IFilesystemRestClient client, ICommandLine subargs) throws FileSystemException
  {
    description = wsDetails.getDescription();
    seed = new ParmsWorkspace(wsDetails.getRepositoryURL(), wsDetails.getItemId());
    
    ReadScopeDTO readScope = wsDetails.getReadScope();
    if (readScope != null) {
      readScope = new ParmsReadScope();
      readScope.scope = readScope.getReadScope();
      
      if (readScope.getDefersTo() != null) {
        readScope.defer_to = readScope.getDefersTo().getItemId().getUuidValue();
      }
    }
    
    if (isStream()) {
      processAreaId = wsDetails.getOwner().getItemId().getUuidValue();
      processAreaType = wsDetails.getOwner().getItemType().getName();
      autoFileLockPatterns = wsDetails.getExclusiveFileLockPatterns();
    }
  }
  
  private void setParams(ParmsPostWorkspace parms, ParmsWorkspace stream, ITeamRepository repo, IFilesystemRestClient client, ICommandLine subargs) throws FileSystemException
  {
    String wsDesc = subargs.getOption(CreateWorkspaceBaseOptions.OPT_DESC, "");
    

    description = wsDesc;
    
    if (stream != null) {
      seed = stream;
    }
    
    ITeamArea teamArea = getTeamArea(subargs, repo, config);
    if (teamArea != null) {
      processAreaId = teamArea.getItemId().getUuidValue();
      processAreaType = teamArea.getItemType().getName();
    } else {
      IProjectArea projectArea = getProjectArea(subargs, repo, config);
      if (projectArea != null) {
        processAreaId = projectArea.getItemId().getUuidValue();
        processAreaType = projectArea.getItemType().getName();
      }
    }
  }
  
  private void updateFlowTargets(WorkspaceDetailsDTO wsDetails, WorkspaceDetailsDTO duplicateWsDetails, ITeamRepository repo, IFilesystemRestClient client)
    throws FileSystemException
  {
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    workspace = new ParmsWorkspace(wsDetails.getRepositoryURL(), wsDetails.getItemId());
    
    List<WorkspaceFlowEntryDTO> wsFlowList = duplicateWsDetails.getFlowEntries();
    flowTargets = new ParmsFlowTargetChange[wsFlowList.size()];
    
    int flowCount = 0;
    for (WorkspaceFlowEntryDTO wsFlow : wsFlowList) {
      ParmsFlowTargetChange parmsFlow = new ParmsFlowTargetChange();
      workspace = new ParmsWorkspace(wsFlow.getRepositoryURL(), wsFlow.getWorkspaceItemId());
      
      List<String> compIds = wsFlow.getScopedComponentItemIds();
      scopedComponentItemIds = ((String[])compIds.toArray(new String[compIds.size()]));
      
      if (wsFlow.isCurrentIncomingFlow()) {
        currentIncomingFlowTarget = workspace;
      }
      if (wsFlow.isCurrentOutgoingFlow()) {
        currentOutgoingFlowTarget = workspace;
      }
      
      if (wsFlow.isDefaultIncomingFlow()) {
        defaultIncomingFlowTarget = workspace;
      }
      if (wsFlow.isDefaultOutgoingFlow()) {
        defaultOutgoingFlowTarget = workspace;
      }
      
      flowTargets[(flowCount++)] = parmsFlow;
    }
    


    if (!isStream()) {
      flowTargetsToRemove = new ParmsWorkspace[1];
      flowTargetsToRemove[0] = new ParmsWorkspace(wsDetails.getRepositoryURL(), 
        duplicateWsDetails.getItemId());
    }
    try
    {
      client.postPutWorkspace(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(getFlowUpdateFailureMsg(), e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
  
  private void updateWithSnapshotInfo(WorkspaceDetailsDTO wsDetails, IBaselineSet snap, ITeamRepository streamOrSnapshotRepo, ITeamRepository repo, IFilesystemRestClient client)
    throws FileSystemException
  {
    ParmsWorkspaceUpdate updateParms = new ParmsWorkspaceUpdate();
    workspaceReplaceWithSnapshot = new ParmsWorkspaceReplaceWithSnapshot[1];
    
    ParmsWorkspaceReplaceWithSnapshot replaceWithSnapshot = new ParmsWorkspaceReplaceWithSnapshot();
    workspace = new ParmsWorkspace(wsDetails.getRepositoryURL(), wsDetails.getItemId());
    baselineSet = new ParmsBaselineSet();
    baselineSet.baselineSetItemId = snap.getItemId().getUuidValue();
    baselineSet.repositoryUrl = streamOrSnapshotRepo.getRepositoryURI();
    
    workspaceReplaceWithSnapshot[0] = replaceWithSnapshot;
    
    ParmsWorkspaceUpdateDilemmaHandler wsUpdateDilemmaHandler = new ParmsWorkspaceUpdateDilemmaHandler();
    componentFlowDirection = "continue";
    
    workspaceUpdateDilemmaHandler = wsUpdateDilemmaHandler;
    
    try
    {
      result = client.postWorkspaceUpdate(updateParms, null);
    } catch (TeamRepositoryException e) { WorkspaceUpdateResultDTO result;
      throw StatusHelper.wrap(NLS.bind(getSnapshotUpdateFailureMsg(), snap.getName()), e, 
        new IndentingPrintStream(config.getContext().stderr()), workspace.repositoryUrl);
    }
    
    WorkspaceUpdateResultDTO result;
    if (result.isSetEclipseReadFailureMessage()) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      for (Object nextMsg : result.getEclipseReadFailureMessage()) {
        err.println((String)nextMsg);
      }
    }
  }
  
  protected String getActiveChangeSetErrorMessageNoName() {
    return com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_UNKNOWN_COMPONENT_HAS_ACTIVE_CHANGES;
  }
  
  protected String getActiveChangeSetErrorMessageWithComponentName() {
    return com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_ACTIVE_CHANGESETS_IN_COMPONENT;
  }
  
  public void validateArguments(ICommandLine subargs) throws FileSystemException {
    int contentArgCount = (subargs.hasOption(CreateWorkspaceBaseOptions.OPT_STREAM) ? 1 : 0) + (
      subargs.hasOption(CreateWorkspaceBaseOptions.OPT_SNAPSHOT) ? 1 : 0);
    if (contentArgCount > 1) {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_MUTUALLY_EXCLUSIVE_OPTIONS, 
        new Object[] {
        subargs.getDefinition().getOption(CreateWorkspaceBaseOptions.OPT_STREAM).getName(), 
        subargs.getDefinition().getOption(CreateWorkspaceBaseOptions.OPT_SNAPSHOT).getName() }));
    }
    


    if ((subargs.hasOption(CreateWorkspaceBaseOptions.OPT_DUPLICATE)) && ((contentArgCount > 0) || 
      (subargs.hasOption(CreateWorkspaceBaseOptions.OPT_DESC)) || (subargs.hasOption(CreateStreamOptions.OPT_TEAMAREA)) || 
      (subargs.hasOption(CreateStreamOptions.OPT_PROJECTAREA)))) {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_DUPLICATE_MUTUALLY_EXCLUSIVE, 
        new String[] { CreateWorkspaceBaseOptions.OPT_STREAM.getName(), CreateWorkspaceBaseOptions.OPT_SNAPSHOT.getName(), 
        CreateStreamOptions.OPT_PROJECTAREA.getName(), CreateStreamOptions.OPT_TEAMAREA.getName(), CreateWorkspaceBaseOptions.OPT_DESC.getName() }));
    }
    
    if (!subargs.hasOption(CreateWorkspaceBaseOptions.OPT_DUPLICATE))
    {

      if (!subargs.hasOption(CreateStreamOptions.OPT_OWNER)) {
        String commandName = SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString();
        throw StatusHelper.argSyntax(NLS.bind(
          com.ibm.team.rtc.cli.infrastructure.internal.Messages.Application_12, 
          commandName, CreateStreamOptions.OPT_OWNER.getName()));
      }
      
      if ((!subargs.hasOption(CreateStreamOptions.OPT_PROJECTAREA)) && (!subargs.hasOption(CreateStreamOptions.OPT_TEAMAREA))) {
        throw StatusHelper.argSyntax(com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_MISSING_OWNER_INFO);
      }
      if ((subargs.hasOption(CreateStreamOptions.OPT_PROJECTAREA)) && (subargs.hasOption(CreateStreamOptions.OPT_TEAMAREA))) {
        throw StatusHelper.argSyntax(NLS.bind(
          com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_MUTUALLY_EXCLUSIVE_OPTIONS, 
          subargs.getDefinition().getOption(CreateStreamOptions.OPT_PROJECTAREA).getName(), 
          subargs.getDefinition().getOption(CreateStreamOptions.OPT_TEAMAREA).getName()));
      }
      
      IScmCommandLineArgument owner = ScmCommandLineArgument.create(subargs.getOptionValue(CreateStreamOptions.OPT_OWNER), config);
      if (subargs.hasOption(CreateStreamOptions.OPT_PROJECTAREA)) {
        SubcommandUtil.validateArgument(owner, RepoUtil.ItemType.PROJECTAREA);
      } else {
        SubcommandUtil.validateArgument(owner, RepoUtil.ItemType.TEAMAREA);
      }
      

    }
    else if (subargs.hasOption(CreateStreamOptions.OPT_OWNER)) {
      String commandName = SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString();
      throw StatusHelper.argSyntax(NLS.bind(
        com.ibm.team.rtc.cli.infrastructure.internal.Messages.Application_11, 
        commandName, CreateStreamOptions.OPT_OWNER.getName()));
    }
  }
  
  public boolean isStream()
  {
    return true;
  }
  
  public String getCreationFailureMsg() {
    return com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_CREATE_FAILURE;
  }
  
  public String getSuccessfulCreationMsg() {
    return com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_SUCCESS;
  }
  
  public String getSnapshotUpdateFailureMsg() {
    return com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_UPDATE_WITH_SNAPSHOT_FAILURE;
  }
  
  public String getFlowUpdateFailureMsg() {
    return com.ibm.team.filesystem.cli.client.internal.Messages.CreateStreamCmd_FLOW_TARGET_UPDATE_FAILURE;
  }
  
  public IProjectArea getProjectArea(ICommandLine subargs, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    IProjectArea projArea = null;
    if (subargs.hasOption(CreateStreamOptions.OPT_PROJECTAREA)) {
      String projAreaSelector = subargs.getOption(CreateStreamOptions.OPT_OWNER);
      projArea = RepoUtil.getProjectArea(repo, projAreaSelector, config);
      if (projArea == null) {
        throw StatusHelper.itemNotFound(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListCmd_NOPROJECTAREA, projAreaSelector));
      }
    }
    
    return projArea;
  }
  
  public ITeamArea getTeamArea(ICommandLine subargs, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    ITeamArea teamArea = null;
    if (subargs.hasOption(CreateStreamOptions.OPT_TEAMAREA)) {
      String teamAreaSelector = subargs.getOption(CreateStreamOptions.OPT_OWNER);
      teamArea = RepoUtil.getTeamArea(teamAreaSelector, null, config, repo);
      if (teamArea == null) {
        throw StatusHelper.itemNotFound(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ListCmd_TeamAreaNotFound, teamAreaSelector));
      }
    }
    
    return teamArea;
  }
  
  public void createComponent(WorkspaceDetailsDTO wsDetails, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine subargs, JSONObject wsObj)
    throws FileSystemException
  {}
}
