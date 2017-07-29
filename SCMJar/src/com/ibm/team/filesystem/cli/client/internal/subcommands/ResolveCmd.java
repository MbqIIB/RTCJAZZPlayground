package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.IConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers.InPlaceConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.portcommand.AbstractPortSubcommand;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.SandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.LoggingHelper;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsAutoMerge;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCommitDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConflictedItemToResolve;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConflictsToResolve;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConflictsToResolveWithProposed;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCreateCurrentPatch;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLineDelimiterDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLocalConflictToResolve;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResolveAsMerged;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResolveLocalConflicts;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResolveWithProposed;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxPaths;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUndoLocalChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUndoLocalChangesRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.conflict.AutoResolveLocalConflictsResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.conflict.ResolveAsMergedResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.conflict.ResolveAutoMergeResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.conflict.ResolveWithProposedResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.UpdateCurrentPatchResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SandboxPathsResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ConflictSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.LocalConflictSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.UnresolvedFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.IConflictItem;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.ILocalConflictItem;
import com.ibm.team.filesystem.rcp.core.internal.changes.util.ComponentSyncModel;
import com.ibm.team.filesystem.rcp.core.internal.compare.AbstractOpenInExternalCompareOperation;
import com.ibm.team.filesystem.rcp.core.internal.compare.OpenConflictInExternalCompareOperation;
import com.ibm.team.filesystem.rcp.core.internal.rest.util.SyncViewDTOUtil;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.common.IComponent;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;


















public class ResolveCmd
  extends AbstractSubcommand
{
  private final char COMPONENT_DELIMITER = '/';
  
  static enum Mode { AUTO,  MERGED,  PROPOSED,  COMPARE,  MOVE,  INLINE_MARKERS,  AUTO_INLINE_MARKERS,  UNDEFINED; }
  
  public ResolveCmd() {}
  
  private class ConflictToResolve { String componentId;
    ConflictSyncDTO conflictItem;
    
    public ConflictToResolve(String compId, ConflictSyncDTO conflictItem) { componentId = compId;
      this.conflictItem = conflictItem;
    }
    
    public String getComponentId() {
      return componentId;
    }
    
    public ConflictSyncDTO getConflictItem() {
      return conflictItem;
    }
  }
  
  public static class LocalConflictToResolve {
    String componentId;
    LocalConflictSyncDTO conflictItem;
    
    public LocalConflictToResolve(String compId, LocalConflictSyncDTO conflictItem) {
      componentId = compId;
      this.conflictItem = conflictItem;
    }
    
    public String getComponentId() {
      return componentId;
    }
    
    public LocalConflictSyncDTO getLocalConflictItem() {
      return conflictItem;
    }
  }
  
  public void run()
    throws FileSystemException
  {
    ICommandLine subargs = config.getSubcommandCommandLine();
    
    Mode mode = Mode.UNDEFINED;
    
    int defined = 0;
    String opt_option = null;
    

    if (subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_AUTO)) {
      defined++;
      mode = Mode.AUTO;
    }
    
    if (subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_MERGED)) {
      defined++;
      mode = Mode.MERGED;
      opt_option = ResolveCmdOpts.OPT_RESOLVE_MERGED.getName();
    }
    
    if (subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_PROPOSED)) {
      defined++;
      mode = Mode.PROPOSED;
      opt_option = ResolveCmdOpts.OPT_RESOLVE_PROPOSED.getName();
    }
    
    if (subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_EXTERNAL_COMPARE)) {
      defined++;
      mode = Mode.COMPARE;
      opt_option = ResolveCmdOpts.OPT_RESOLVE_EXTERNAL_COMPARE.getName();
    }
    
    if (subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_MOVE)) {
      defined++;
      mode = Mode.MOVE;
      opt_option = ResolveCmdOpts.OPT_RESOLVE_MOVE.getName();
    }
    
    if (subargs.hasOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER)) {
      switch (mode)
      {
      case AUTO: 
        mode = Mode.AUTO_INLINE_MARKERS;
        break;
      
      case AUTO_INLINE_MARKERS: 
      case COMPARE: 
      case INLINE_MARKERS: 
      case MERGED: 
        throw StatusHelper.argSyntax(
          NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_6, 
          new String[] {
          opt_option, 
          AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER.getName() }));
      
      default: 
        mode = Mode.INLINE_MARKERS;
      }
      
    }
    
    if ((mode == Mode.INLINE_MARKERS) && (!subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_LOCAL))) {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_INVALID_INPLACE_CONFLICT_MARKER_REQUEST, 
        AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER.getName()));
    }
    

    if ((defined > 1) || ((defined == 0) && ((mode != Mode.INLINE_MARKERS) || (!subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_LOCAL))))) {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_1, new String[] {
        ResolveCmdOpts.OPT_RESOLVE_MERGED.getName(), ResolveCmdOpts.OPT_RESOLVE_PROPOSED.getName(), 
        ResolveCmdOpts.OPT_RESOLVE_AUTO.getName(), ResolveCmdOpts.OPT_RESOLVE_EXTERNAL_COMPARE.getName(), 
        ResolveCmdOpts.OPT_RESOLVE_MOVE.getName() }));
    }
    

    String externalCompareTool = null;
    

    if ((mode == Mode.AUTO) || (mode == Mode.AUTO_INLINE_MARKERS)) {
      if ((!subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_LOCAL)) && (subargs.hasOption(ResolveCmdOpts.OPT_TO_RESOLVE))) {
        String commandName = SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString();
        throw StatusHelper.argSyntax(NLS.bind(
          com.ibm.team.rtc.cli.infrastructure.internal.Messages.Application_11, 
          commandName, ResolveCmdOpts.OPT_TO_RESOLVE.getName()));
      }
    } else if (mode == Mode.COMPARE)
    {
      if (subargs.hasOption(ResolveCmdOpts.OPT_TO_RESOLVE)) {
        throw StatusHelper.argSyntax(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_EXTERNAL_COMPARE_ONE_FILE);
      }
      externalCompareTool = DiffCmd.getExternalCompareTool((ScmClientConfiguration)config, true);
    } else if (((mode == Mode.MERGED) || (mode == Mode.PROPOSED) || (mode == Mode.MOVE)) && (!subargs.hasOption(ResolveCmdOpts.OPT_TO_RESOLVE))) {
      String commandName = SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString();
      throw StatusHelper.argSyntax(NLS.bind(
        com.ibm.team.rtc.cli.infrastructure.internal.Messages.Application_12, 
        commandName, ResolveCmdOpts.OPT_TO_RESOLVE.getName()));
    }
    

    ResourcesPlugin.getWorkspace();
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
    Map<String, ISandboxWorkspace> wsIdtoWsInSandboxMap = new HashMap();
    
    for (ISandboxWorkspace wsInSandbox : wsInSandboxList) {
      wsIdtoWsInSandboxMap.put(wsInSandbox.getWorkspaceItemId(), wsInSandbox);
    }
    
    List<UndoCmd.Change> changesToResolveList = null;
    if ((mode != Mode.AUTO) && (mode != Mode.AUTO_INLINE_MARKERS)) {
      Object resources = mode == Mode.COMPARE ? 
        Collections.singletonList(subargs.getOption(ResolveCmdOpts.OPT_RESOLVE_EXTERNAL_COMPARE)) : 
        subargs.getOptions(ResolveCmdOpts.OPT_TO_RESOLVE);
      changesToResolveList = getChangesToResolve(wsIdtoWsInSandboxMap, client, (List)resources);
    } else if (subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_LOCAL))
    {
      changesToResolveList = getChangesToResolve(wsIdtoWsInSandboxMap, client, subargs.getOptions(ResolveCmdOpts.OPT_TO_RESOLVE));
    }
    

    Object wsList = new ArrayList(wsIdtoWsInSandboxMap.values().size());
    for (ISandboxWorkspace wsInSandbox : wsIdtoWsInSandboxMap.values()) {
      String uri = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
        Collections.singletonList(wsInSandbox));
      RepoUtil.login(config, client, config.getConnectionInfo(uri));
      ((List)wsList).add(new ParmsWorkspace(uri, wsInSandbox.getWorkspaceItemId()));
    }
    

    SyncViewDTO syncView = SubcommandUtil.getSyncView((List)wsList, true, client, config);
    
    boolean showSuccessMessage = true;
    if (subargs.hasOption(ResolveCmdOpts.OPT_RESOLVE_LOCAL))
    {

      switch (mode)
      {
      case AUTO_INLINE_MARKERS: 
        Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap = findLocalConflictsToResolve(syncView, changesToResolveList, client, config);
        resolveLocalConflictsAsMerged(ws2ConflictItemsMap, client, config);
        break;
      case COMPARE: 
        List<ParmsUndoLocalChangesRequest> undoLocalChangeRequests = generateUndoLocalChangeRequests(changesToResolveList, syncView, client, config);
        resolveLocalConflictsAsProposed(undoLocalChangeRequests, client, config);
        break;
      case AUTO: 
      case PROPOSED: 
        Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap = findLocalConflictsToResolve(syncView, changesToResolveList, client, config);
        resolveAutomaticallyLocalConflicts(ws2ConflictItemsMap, client, config, subargs);
        break;
      case MOVE: 
        Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap = findLocalConflictsToResolve(syncView, changesToResolveList, client, config);
        resolveLocalConflictsInlineMarkers(ws2ConflictItemsMap, client, config, subargs);
        break;
      case INLINE_MARKERS: 
        showSuccessMessage = false;
        

        Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap = findLocalConflictsToResolve(syncView, changesToResolveList, client, config);
        LocalConflictToResolve conflict = (LocalConflictToResolve)((List)ws2ConflictItemsMap.values().iterator().next()).get(0);
        resolveExternally(conflictItem, client, config, subargs, externalCompareTool);
      }
      if (showSuccessMessage) {
        config.getContext().stdout().println(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_Success);
      }
      return;
    }
    

    Map<ParmsWorkspace, List<ConflictToResolve>> ws2ConflictItemsMap = findConflictsToResolve(syncView, changesToResolveList, client, config);
    

    switch (mode) {
    case AUTO_INLINE_MARKERS: 
      resolveAsMerged(ws2ConflictItemsMap, client, config);
      break;
    case COMPARE: 
      resolveAsProposed(ws2ConflictItemsMap, client, config);
      break;
    case MERGED: 
      List<ConflictToResolve> conflictsToMove = null;
      ParmsWorkspace ws = null;
      if (ws2ConflictItemsMap.size() != 1) {
        throw StatusHelper.argSyntax(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_0);
      }
      for (Map.Entry<ParmsWorkspace, List<ConflictToResolve>> entry : ws2ConflictItemsMap.entrySet()) {
        ws = (ParmsWorkspace)entry.getKey();
        conflictsToMove = (List)entry.getValue();
      }
      String compSelector = subargs.getOption(ResolveCmdOpts.OPT_RESOLVE_MOVE);
      WorkspaceComponentDTO component = RepoUtil.getComponent(ws, compSelector, client, config);
      config.getContext().stdout().println(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_3, component.getName()));
      createCurrentPatch(ws, component, conflictsToMove, client, config);
      config.getContext().stdout().println(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_5);
      showSuccessMessage = false;
      break;
    case INLINE_MARKERS: 
      showSuccessMessage = false;
      

      ConflictToResolve conflict = (ConflictToResolve)((List)ws2ConflictItemsMap.values().iterator().next()).get(0);
      resolveExternally(conflictItem, client, config, subargs, externalCompareTool);
      break;
    case AUTO: 
    case PROPOSED: 
      resolveAutomatically(ws2ConflictItemsMap, client, config, subargs);
      break;
    case MOVE: 
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_INVALID_INPLACE_CONFLICT_MARKER_REQUEST, 
        AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER.getName()));
    }
    
    if (showSuccessMessage) {
      config.getContext().stdout().println(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_Success);
    }
  }
  


  private void createCurrentPatch(ParmsWorkspace ws, WorkspaceComponentDTO component, List<ConflictToResolve> conflictsToMove, IFilesystemRestClient client, IScmClientConfiguration config)
    throws CLIFileSystemClientException, FileSystemException
  {
    ParmsCreateCurrentPatch parms = new ParmsCreateCurrentPatch();
    workspace = ws;
    componentItemId = component.getItemId();
    conflicts = new ParmsConflictsToResolve[conflictsToMove.size()];
    int i = 0;
    for (ConflictToResolve conflictToResolve : conflictsToMove) {
      ParmsConflictsToResolve conflictParm = new ParmsConflictsToResolve();
      versionableItemId = conflictToResolve.getConflictItem().getVersionableItemId();
      conflictType = conflictToResolve.getConflictItem().getConflictType();
      kind = conflictToResolve.getConflictItem().getKind();
      componentItemId = conflictToResolve.getComponentId();
      conflicts[(i++)] = conflictParm;
    }
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    
    pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    try
    {
      result = client.postCreateCurrentPatch(parms, null);
    } catch (TeamRepositoryException e) { UpdateCurrentPatchResultDTO result;
      throw StatusHelper.wrap(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_2, e, new IndentingPrintStream(config.getContext().stderr()), workspace.repositoryUrl);
    }
    UpdateCurrentPatchResultDTO result;
    if ((result.isCancelled()) && 
      (result.getOutOfSyncShares().size() > 0)) {
      AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
    }
    

    if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
      SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), 
        new IndentingPrintStream(config.getContext().stdout()));
    }
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    AbstractPortSubcommand.printPorts(ws, component.getItemId(), client, out, config);
  }
  
  private List<UndoCmd.Change> getChangesToResolve(Map<String, ISandboxWorkspace> wsIdtoWsInSandboxMap, IFilesystemRestClient client, List<String> resources) throws FileSystemException
  {
    List<String> unProcessedResources = new ArrayList();
    

    List<UndoCmd.Change> changesToResolveList = getChangesToResolve(wsIdtoWsInSandboxMap, client, resources, false, 
      unProcessedResources);
    
    if (unProcessedResources.size() > 0)
    {
      List<UndoCmd.Change> remainingChangesToResolveList = getChangesToResolve(wsIdtoWsInSandboxMap, client, resources, 
        true, null);
      if (remainingChangesToResolveList.size() > 0) {
        changesToResolveList.addAll(remainingChangesToResolveList);
      }
    }
    
    return changesToResolveList;
  }
  
  private class Resource {
    String selector;
    String osPath;
    String componentSelector;
    
    public Resource(String selector, String osPath, String componentSelector) {
      this.selector = selector;
      this.osPath = osPath;
      this.componentSelector = componentSelector;
    }
  }
  
  private List<UndoCmd.Change> getChangesToResolve(Map<String, ISandboxWorkspace> wsIdtoWsInSandboxMap, IFilesystemRestClient client, List<String> resources, boolean componentPrefixMode, List<String> unprocessedResources)
    throws FileSystemException
  {
    if (unprocessedResources != null) {
      unprocessedResources.clear();
    }
    

    List<Resource> resourceList = new ArrayList(resources.size());
    String osPath; for (String resource : resources) {
      String selector = resource;
      String componentSelector = null;
      if (componentPrefixMode) {
        String[] result = splitComponentAndItem(resource, null, null);
        componentSelector = result[0];
        selector = result[1];
      }
      ILocation changePath = SubcommandUtil.makeAbsolutePath(config, selector);
      osPath = changePath.toOSString();
      resourceList.add(new Resource(resource, osPath, componentSelector));
    }
    

    ParmsSandboxPaths parmsPaths = new ParmsSandboxPaths();
    includeNonRegisteredSandboxes = true;
    pathsToResolve = new String[resourceList.size()];
    for (int count = 0; count < resourceList.size(); count++) {
      pathsToResolve[count] = getosPath;
    }
    
    SandboxPathsResultDTO pathsResult = null;
    try {
      pathsResult = client.getSandboxPaths(parmsPaths, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(com.ibm.team.filesystem.cli.client.internal.Messages.Common_UNABLE_TO_GET_SANDBOX_PATHS, e, new IndentingPrintStream(config.getContext().stderr()), null);
    }
    

    List<UndoCmd.Change> changesToResolveList = new ArrayList();
    
    int count = 0;
    for (ShareableDTO shareable : pathsResult.getPaths()) {
      Resource resource = (Resource)resourceList.get(count);
      ResourcePropertiesDTO resourceProps = null;
      boolean foundShareable = false;
      
      if ((shareable.getSandboxPath() != null) && (shareable.getSandboxPath().length() > 0) && 
        (shareable.getRelativePath() != null) && (shareable.getRelativePath().getSegments().size() > 0)) {
        try {
          resourceProps = RepoUtil.getResourceProperties(osPath, client, config);
          ShareDTO share = resourceProps.getShare();
          if (!wsIdtoWsInSandboxMap.keySet().contains(share.getContextItemId())) {
            wsIdtoWsInSandboxMap.put(
              share.getContextItemId(), 
              new SandboxWorkspace(share.getContextItemId(), share
              .getContextName(), share.getRepositoryId()));
          }
          if (componentPrefixMode) {
            if (componentSelector != null) {
              ITeamRepository repository = RepoUtil.getTeamRepository(UUID.valueOf(share.getRepositoryId()));
              RepoUtil.getComponent(
                new ParmsWorkspace(repository.getRepositoryURI(), share.getContextItemId()), 
                componentSelector, client, config);
              foundShareable = true;
            }
          } else {
            foundShareable = true;
          }
          if (foundShareable) {
            changesToResolveList.add(new UndoCmd.Change(selector, shareable, resourceProps));
          }
        }
        catch (Exception localException) {}
      }
      

      if (!foundShareable) {
        IUuidAliasRegistry.IUuidAlias uuidAlias = RepoUtil.lookupUuidAndAlias(selector);
        if ((uuidAlias != null) || (componentPrefixMode))
        {

          changesToResolveList.add(new UndoCmd.Change(selector, null, null));


        }
        else if (unprocessedResources != null) {
          unprocessedResources.add(selector);
        }
      }
      

      count++;
    }
    
    return changesToResolveList;
  }
  
  private void resolveAsProposed(Map<ParmsWorkspace, List<ConflictToResolve>> ws2ConflictItemsMap, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    for (Map.Entry<ParmsWorkspace, List<ConflictToResolve>> entry : ws2ConflictItemsMap.entrySet()) {
      ParmsResolveWithProposed parms = new ParmsResolveWithProposed();
      workspace = ((ParmsWorkspace)entry.getKey());
      
      List<ParmsConflictsToResolveWithProposed> conflictsToResolve = new ArrayList();
      
      for (ConflictToResolve conflict : (List)entry.getValue()) {
        ParmsConflictsToResolveWithProposed parmsConflict = new ParmsConflictsToResolveWithProposed();
        componentItemId = conflict.getComponentId();
        versionableItemId = conflict.getConflictItem().getVersionableItemId();
        kind = conflict.getConflictItem().getKind();
        conflictType = conflict.getConflictItem().getConflictType();
        


        conflictsToResolve.add(parmsConflict);
      }
      conflictsToResolve = ((ParmsConflictsToResolveWithProposed[])conflictsToResolve.toArray(new ParmsConflictsToResolveWithProposed[conflictsToResolve.size()]));
      
      outOfSyncInstructions = new ParmsOutOfSyncInstructions();
      outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
      outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
      
      sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
      
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
      

      missingRequiredChangesDilemmaHandler = "continue";
      
      try
      {
        result = client.postResolveWithProposed(parms, null);
      } catch (TeamRepositoryException e) { ResolveWithProposedResultDTO result;
        throw StatusHelper.wrap(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_4, e, new IndentingPrintStream(config.getContext().stderr()), workspace.repositoryUrl);
      }
      ResolveWithProposedResultDTO result;
      if ((result.isCancelled()) && 
        (result.getOutOfSyncShares().size() > 0)) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      

      if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
        SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), 
          new IndentingPrintStream(config.getContext().stdout()));
      }
    }
  }
  
  private void resolveAsMerged(Map<ParmsWorkspace, List<ConflictToResolve>> ws2ConflictItemsMap, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    for (Map.Entry<ParmsWorkspace, List<ConflictToResolve>> entry : ws2ConflictItemsMap.entrySet()) {
      ParmsResolveAsMerged parms = new ParmsResolveAsMerged();
      workspace = ((ParmsWorkspace)entry.getKey());
      
      List<ParmsConflictsToResolve> conflictsToResolve = new ArrayList();
      
      for (ConflictToResolve conflict : (List)entry.getValue()) {
        ParmsConflictsToResolve parmsConflict = new ParmsConflictsToResolve();
        componentItemId = conflict.getComponentId();
        versionableItemId = conflict.getConflictItem().getVersionableItemId();
        kind = conflict.getConflictItem().getKind();
        conflictType = conflict.getConflictItem().getConflictType();
        
        conflictsToResolve.add(parmsConflict);
      }
      conflictsToResolve = ((ParmsConflictsToResolve[])conflictsToResolve.toArray(new ParmsConflictsToResolve[conflictsToResolve.size()]));
      
      preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
      
      pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
      if (config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
      } else {
        pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
      }
      
      outOfSyncInstructions = new ParmsOutOfSyncInstructions();
      outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
      outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
      
      sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
      sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
      
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
      sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
      

      missingRequiredChangesDilemmaHandler = "continue";
      unmergedChangesDilemmaHandler = "continue";
      
      try
      {
        result = client.postResolveAsMerged(parms, null);
      } catch (TeamRepositoryException e) { ResolveAsMergedResultDTO result;
        throw StatusHelper.wrap(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_4, e, new IndentingPrintStream(config.getContext().stderr()), workspace.repositoryUrl);
      }
      ResolveAsMergedResultDTO result;
      if (result.isCancelled()) {
        if (result.getOutOfSyncShares().size() > 0) {
          AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
        }
        
        int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
        if (noOfUncheckedInChanges > 0) {
          throw StatusHelper.uncheckedInChanges(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
        }
      }
      
      if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
        SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), 
          new IndentingPrintStream(config.getContext().stdout()));
      }
    }
  }
  
  private void resolveLocalConflictsAsMerged(Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    for (Map.Entry<ParmsWorkspace, List<LocalConflictToResolve>> entry : ws2ConflictItemsMap.entrySet()) {
      ParmsResolveLocalConflicts parms = new ParmsResolveLocalConflicts();
      workspace = ((ParmsWorkspace)entry.getKey());
      
      List<ParmsLocalConflictToResolve> conflictsToResolve = new ArrayList();
      
      for (LocalConflictToResolve conflict : (List)entry.getValue()) {
        ParmsLocalConflictToResolve parmsConflict = new ParmsLocalConflictToResolve();
        componentItemId = conflict.getComponentId();
        versionableItemId = conflict.getLocalConflictItem().getVersionableItemId();
        sandboxPath = conflict.getLocalConflictItem().getSandboxPath();
        conflictsToResolve.add(parmsConflict);
      }
      localConflictsToResolve = ((ParmsLocalConflictToResolve[])conflictsToResolve.toArray(new ParmsLocalConflictToResolve[conflictsToResolve.size()]));
      try
      {
        client.postResolveLocalConflictsAsMerged(parms, null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_4, e, new IndentingPrintStream(config.getContext().stderr()), workspace.repositoryUrl);
      }
    }
  }
  
  private void resolveLocalConflictsAsProposed(List<ParmsUndoLocalChangesRequest> undoLocalChangesRequests, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsUndoLocalChanges parmsLocalChanges = new ParmsUndoLocalChanges();
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = false;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    undoRequests = ((ParmsUndoLocalChangesRequest[])undoLocalChangesRequests.toArray(new ParmsUndoLocalChangesRequest[undoLocalChangesRequests.size()]));
    try
    {
      client.postUndoLocalChanges(parmsLocalChanges, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(com.ibm.team.filesystem.cli.client.internal.Messages.UndoCmd_6, e, new IndentingPrintStream(config.getContext().stderr()));
    }
  }
  
  private Map<ParmsWorkspace, List<ConflictToResolve>> findConflictsToResolve(SyncViewDTO syncView, List<UndoCmd.Change> changesToResolveList, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<ParmsWorkspace, List<ConflictToResolve>> ws2ConflictItemsMap = new HashMap();
    Map<String, ParmsWorkspace> wsId2WsMap = new HashMap();
    Set<String> conflictItems = new HashSet();
    Set<String> conflictItemsWithSameItemId = new HashSet();
    Iterator localIterator2;
    UnresolvedFolderSyncDTO unresolvedFolderSync;
    Iterator localIterator4; for (Iterator localIterator1 = syncView.getWorkspaces().iterator(); localIterator1.hasNext(); 
        





        localIterator2.hasNext())
    {
      WorkspaceSyncDTO wsSync = (WorkspaceSyncDTO)localIterator1.next();
      ParmsWorkspace ws = (ParmsWorkspace)wsId2WsMap.get(wsSync.getWorkspaceItemId());
      if (ws == null) {
        ws = new ParmsWorkspace(wsSync.getRepositoryUrl(), wsSync.getWorkspaceItemId());
        wsId2WsMap.put(wsSync.getWorkspaceItemId(), ws);
      }
      
      localIterator2 = wsSync.getComponents().iterator(); continue;ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator2.next();
      for (Iterator localIterator3 = compSync.getUnresolved().iterator(); localIterator3.hasNext(); 
          localIterator4.hasNext())
      {
        unresolvedFolderSync = (UnresolvedFolderSyncDTO)localIterator3.next();
        localIterator4 = unresolvedFolderSync.getConflicts().iterator(); continue;ConflictSyncDTO conflictSync = (ConflictSyncDTO)localIterator4.next();
        List<ConflictToResolve> conflicts = (List)ws2ConflictItemsMap.get(ws);
        if (conflicts == null) {
          conflicts = new ArrayList();
          ws2ConflictItemsMap.put(ws, conflicts);
        }
        conflicts.add(new ConflictToResolve(compSync.getComponentItemId(), conflictSync));
        if (!conflictItems.add(conflictSync.getVersionableItemId())) {
          conflictItemsWithSameItemId.add(conflictSync.getVersionableItemId());
        }
      }
    }
    



    if (changesToResolveList == null) {
      return ws2ConflictItemsMap;
    }
    

    conflictItems.clear();
    conflictItems = null;
    

    Map<ParmsWorkspace, List<ConflictToResolve>> ws2ConflictItemsToResolveMap = new HashMap();
    
    label798:
    
    for (UndoCmd.Change change : changesToResolveList) {
      boolean found = false;
      String componentId = null;
      String changeSelector = null;
      ShareDTO share = change.hasResourceInfo() ? change.getResourceProperties().getShare() : null;
      
      Iterator localIterator5;
      for (localIterator4 = ws2ConflictItemsMap.entrySet().iterator(); localIterator4.hasNext(); 
          
          localIterator5.hasNext())
      {
        Map.Entry<ParmsWorkspace, List<ConflictToResolve>> map = (Map.Entry)localIterator4.next();
        if ((share != null) && (!share.getContextItemId().equals(getKeyworkspaceItemId))) break label798;
        localIterator5 = ((List)map.getValue()).iterator(); continue;ConflictToResolve toResolve = (ConflictToResolve)localIterator5.next();
        if ((share == null) || (share.getComponentItemId().equals(componentId))) {
          boolean matched = matchItem(change, toResolve.getConflictItem());
          


          if (!matched) {
            try {
              if (changeSelector == null) {
                String[] result = splitComponentAndItem(change.getSelector(), ((ParmsWorkspace)map.getKey()).getTeamRepository(), config);
                componentId = result[0];
                changeSelector = result[1];
              }
              

              if ((componentId != null) && (componentId.equals(componentId))) {
                change = new UndoCmd.Change(changeSelector, shareable, resource);
                matched = matchItem(change, toResolve.getConflictItem());
              }
            }
            catch (TeamRepositoryException localTeamRepositoryException) {
              matched = false;
              
              changeSelector = change.getSelector();
            }
          }
          
          if (matched) {
            if ((share == null) && (componentId == null) && 
              (conflictItemsWithSameItemId.contains(change.getItemId()))) {
              throw StatusHelper.ambiguousSelector(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_MULTIPLE_CONFLICTS_SAME_ITEM, change.getSelector()));
            }
            List<ConflictToResolve> conflicts = (List)ws2ConflictItemsToResolveMap.get(map.getKey());
            if (conflicts == null) {
              conflicts = new ArrayList();
              ws2ConflictItemsToResolveMap.put((ParmsWorkspace)map.getKey(), conflicts);
            }
            conflicts.add(toResolve);
            
            found = true;
            break;
          }
        }
      }
      


      if (!found) {
        throw StatusHelper.inappropriateArgument(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_PATH_NOT_IN_CONFLICT, change.getSelector()));
      }
    }
    
    return ws2ConflictItemsToResolveMap;
  }
  
  private String[] splitComponentAndItem(String selector, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException {
    String[] result = new String[2];
    int index = selector.indexOf('/');
    if (index > 0)
    {
      result[0] = selector.substring(0, index);
      result[1] = selector.substring(index + 1);
      
      if ((repo != null) && (config != null)) {
        IComponent component = RepoUtil.getComponent(result[0], repo, config);
        result[0] = component.getItemId().getUuidValue();
      }
    } else {
      result[1] = selector;
    }
    return result;
  }
  
  private Map<ParmsWorkspace, List<LocalConflictToResolve>> findLocalConflictsToResolve(SyncViewDTO syncView, List<UndoCmd.Change> changesToResolveList, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap = new HashMap();
    Map<String, ParmsWorkspace> wsId2WsMap = new HashMap();
    Set<String> conflictItems = new HashSet();
    Set<String> conflictItemsWithSameItemId = new HashSet();
    Iterator localIterator2;
    UnresolvedFolderSyncDTO unresolvedFolderSync;
    Iterator localIterator4; for (Iterator localIterator1 = syncView.getWorkspaces().iterator(); localIterator1.hasNext(); 
        





        localIterator2.hasNext())
    {
      WorkspaceSyncDTO wsSync = (WorkspaceSyncDTO)localIterator1.next();
      ParmsWorkspace ws = (ParmsWorkspace)wsId2WsMap.get(wsSync.getWorkspaceItemId());
      if (ws == null) {
        ws = new ParmsWorkspace(wsSync.getRepositoryUrl(), wsSync.getWorkspaceItemId());
        wsId2WsMap.put(wsSync.getWorkspaceItemId(), ws);
      }
      
      localIterator2 = wsSync.getComponents().iterator(); continue;ComponentSyncDTO compSync = (ComponentSyncDTO)localIterator2.next();
      for (Iterator localIterator3 = compSync.getUnresolved().iterator(); localIterator3.hasNext(); 
          localIterator4.hasNext())
      {
        unresolvedFolderSync = (UnresolvedFolderSyncDTO)localIterator3.next();
        localIterator4 = unresolvedFolderSync.getLocalConflicts().iterator(); continue;LocalConflictSyncDTO conflictSync = (LocalConflictSyncDTO)localIterator4.next();
        List<LocalConflictToResolve> conflicts = (List)ws2ConflictItemsMap.get(ws);
        if (conflicts == null) {
          conflicts = new ArrayList();
          ws2ConflictItemsMap.put(ws, conflicts);
        }
        conflicts.add(new LocalConflictToResolve(compSync.getComponentItemId(), conflictSync));
        if (!conflictItems.add(conflictSync.getVersionableItemId())) {
          conflictItemsWithSameItemId.add(conflictSync.getVersionableItemId());
        }
      }
    }
    



    if (changesToResolveList == null) {
      return ws2ConflictItemsMap;
    }
    

    conflictItems.clear();
    conflictItems = null;
    

    Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsToResolveMap = new HashMap();
    
    label797:
    
    for (UndoCmd.Change change : changesToResolveList) {
      boolean found = false;
      String componentId = null;
      String changeSelector = null;
      ShareDTO share = change.hasResourceInfo() ? change.getResourceProperties().getShare() : null;
      
      Iterator localIterator5;
      for (localIterator4 = ws2ConflictItemsMap.entrySet().iterator(); localIterator4.hasNext(); 
          
          localIterator5.hasNext())
      {
        Map.Entry<ParmsWorkspace, List<LocalConflictToResolve>> map = (Map.Entry)localIterator4.next();
        if ((share != null) && (!share.getContextItemId().equals(getKeyworkspaceItemId))) break label797;
        localIterator5 = ((List)map.getValue()).iterator(); continue;LocalConflictToResolve toResolve = (LocalConflictToResolve)localIterator5.next();
        if ((share == null) || (share.getComponentItemId().equals(componentId))) {
          boolean matched = matchItem(change, toResolve.getLocalConflictItem());
          


          if (!matched) {
            try {
              if (changeSelector == null) {
                String[] result = splitComponentAndItem(change.getSelector(), ((ParmsWorkspace)map.getKey()).getTeamRepository(), config);
                componentId = result[0];
                changeSelector = result[1];
              }
              

              if ((componentId != null) && (componentId.equals(componentId))) {
                change = new UndoCmd.Change(changeSelector, shareable, resource);
                matched = matchItem(change, toResolve.getLocalConflictItem());
              }
            }
            catch (TeamRepositoryException localTeamRepositoryException) {
              matched = false;
              
              changeSelector = change.getSelector();
            }
          }
          
          if (matched) {
            if ((share == null) && (componentId == null) && 
              (conflictItemsWithSameItemId.contains(change.getItemId()))) {
              throw StatusHelper.ambiguousSelector(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_MULTIPLE_CONFLICTS_SAME_ITEM, change.getSelector()));
            }
            List<LocalConflictToResolve> conflicts = (List)ws2ConflictItemsToResolveMap.get(map.getKey());
            if (conflicts == null) {
              conflicts = new ArrayList();
              ws2ConflictItemsToResolveMap.put((ParmsWorkspace)map.getKey(), conflicts);
            }
            conflicts.add(toResolve);
            
            found = true;
            break;
          }
        }
      }
      


      if (!found) {
        throw StatusHelper.inappropriateArgument(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_PATH_NOT_IN_CONFLICT, change.getSelector()));
      }
    }
    
    return ws2ConflictItemsToResolveMap;
  }
  
  private List<ParmsUndoLocalChangesRequest> generateUndoLocalChangeRequests(List<UndoCmd.Change> changesToFind, SyncViewDTO syncView, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap = findLocalConflictsToResolve(
      syncView, changesToFind, client, config);
    
    List<ParmsUndoLocalChangesRequest> undoLocalChangesRequests = new ArrayList();
    Iterator localIterator2; for (Iterator localIterator1 = ws2ConflictItemsMap.entrySet().iterator(); localIterator1.hasNext(); 
        


        localIterator2.hasNext())
    {
      Map.Entry<ParmsWorkspace, List<LocalConflictToResolve>> map = (Map.Entry)localIterator1.next();
      String workspaceItemId = getKeyworkspaceItemId;
      String repositoryUrl = getKeyrepositoryUrl;
      
      localIterator2 = ((List)map.getValue()).iterator(); continue;LocalConflictToResolve localConflict = (LocalConflictToResolve)localIterator2.next();
      boolean foundChange = false;
      
      for (ParmsUndoLocalChangesRequest localCR : undoLocalChangesRequests) {
        if ((workspace.workspaceItemId.equals(workspaceItemId)) && 
          (componentItemId.equals(localConflict.getComponentId())) && 
          (sandboxPath.equals(localConflict.getLocalConflictItem().getSandboxPath())))
        {

          addConflictToUndoRequest(localCR, localConflict.getLocalConflictItem());
          foundChange = true;
          break;
        }
      }
      
      if (!foundChange) {
        ParmsUndoLocalChangesRequest localChangeRequest = new ParmsUndoLocalChangesRequest();
        workspace = new ParmsWorkspace(repositoryUrl, workspaceItemId);
        componentItemId = localConflict.getComponentId();
        sandboxPath = localConflict.getLocalConflictItem().getSandboxPath();
        versionableItemIds = new String[0];
        addConflictToUndoRequest(localChangeRequest, localConflict.getLocalConflictItem());
        undoLocalChangesRequests.add(localChangeRequest);
      }
    }
    

    return undoLocalChangesRequests;
  }
  
  private void addConflictToUndoRequest(ParmsUndoLocalChangesRequest localCR, LocalConflictSyncDTO localConflict)
  {
    int length = versionableItemIds.length;
    String[] newValue;
    String[] newValue; if (localConflict.getDeletedDuringReplayVersionableItemId() == null) {
      newValue = new String[length + 1];
    } else {
      newValue = new String[length + 2];
    }
    System.arraycopy(versionableItemIds, 0, newValue, 0, length);
    newValue[length] = localConflict.getVersionableItemId();
    if (localConflict.getDeletedDuringReplayVersionableItemId() != null) {
      newValue[(length + 1)] = localConflict.getDeletedDuringReplayVersionableItemId();
    }
    versionableItemIds = newValue;
  }
  
  public static boolean matchItem(UndoCmd.Change change, ConflictSyncDTO conflictSync)
  {
    if (conflictSync.getVersionableItemId().equals(change.getItemId())) {
      return true;
    }
    

    if ((!conflictSync.isSetCommonAncestorVersionableStateId()) && (change.hasShareInfo())) {
      String path = conflictSync.isSetNewPathHint() ? conflictSync.getNewPathHint() : 
        conflictSync.getPathHint();
      if ((path != null) && (path.equalsIgnoreCase(change.getRelativePathToComponent()))) {
        return true;
      }
    }
    
    return false;
  }
  
  public static boolean matchItem(UndoCmd.Change change, LocalConflictSyncDTO localConflictSync)
  {
    if (localConflictSync.getVersionableItemId().equals(change.getItemId())) {
      return true;
    }
    return false;
  }
  


  private void resolveLocalConflictsInlineMarkers(Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine subargs)
    throws FileSystemException
  {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    

    File cfaRootPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
    if (cfaRootPath != null) {
      IPath cfaRoot = new Path(cfaRootPath.getAbsolutePath());
      IConflictHandler handler = new InPlaceConflictHandler();
      
      handler.handleLocalConflicts(cfaRoot, ws2ConflictItemsMap, client, config);
      
      out.println(com.ibm.team.filesystem.cli.client.internal.Messages.LocalConflicts_InPlaceMarkers_Help);
    }
  }
  


  private void resolveAutomaticallyLocalConflicts(Map<ParmsWorkspace, List<LocalConflictToResolve>> ws2ConflictItemsMap, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine subargs)
    throws FileSystemException
  {
    List<ParmsWorkspace> unresolvedWorkspaces = new ArrayList();
    List<ShareableDTO> deletedContentShareables = new ArrayList();
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    for (Map.Entry<ParmsWorkspace, List<LocalConflictToResolve>> entryMap : ws2ConflictItemsMap.entrySet())
    {
      ParmsResolveLocalConflicts parms = new ParmsResolveLocalConflicts();
      
      workspace = new ParmsWorkspace();
      workspace.repositoryUrl = getKeyrepositoryUrl;
      workspace.workspaceItemId = getKeyworkspaceItemId;
      localConflictsToResolve = new ParmsLocalConflictToResolve[((List)entryMap.getValue()).size()];
      
      int count = 0;
      String sandboxPath = null;
      for (LocalConflictToResolve conflictToResolve : (List)entryMap.getValue()) {
        ParmsLocalConflictToResolve itemToResolve = new ParmsLocalConflictToResolve();
        componentItemId = conflictToResolve.getComponentId();
        versionableItemId = conflictToResolve.getLocalConflictItem().getVersionableItemId();
        if (sandboxPath == null)
        {
          sandboxPath = conflictToResolve.getLocalConflictItem().getSandboxPath();
        }
        localConflictsToResolve[(count++)] = itemToResolve;
      }
      sandboxPath = sandboxPath;
      
      AutoResolveLocalConflictsResultDTO result = null;
      try {
        result = client.postAutoResolveLocalConflicts(parms, null);
      } catch (TeamRepositoryException e) {
        String wsName = getKeyworkspaceItemId;
        try {
          ((ParmsWorkspace)entryMap.getKey()).getWorkspaceConnection(null).getName();
        }
        catch (Exception localException) {}
        
        config.getContext().stderr().println(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.AcceptCmd_CONFLICT_RESOLUTION_FAILED, wsName));
        LoggingHelper.log("com.ibm.team.filesystem.cli.client", e);
      }
      
      if ((result != null) && (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0)) {
        deletedContentShareables.addAll(result.getSandboxUpdateDilemma().getDeletedContentShareables());
      }
      
      if ((result == null) || (result.getNumberConflictsResolved() != localConflictsToResolve.length)) {
        unresolvedWorkspaces.add((ParmsWorkspace)entryMap.getKey());
      }
    }
    
    if (deletedContentShareables.size() > 0) {
      SubcommandUtil.showDeletedContent(com.ibm.team.filesystem.cli.client.internal.Messages.AcceptResultDisplayer_DELETED_CONTENT_WHEN_AUTOMERGING, 
        deletedContentShareables, out);
    }
    
    if ((unresolvedWorkspaces.size() > 0) && 
      (subargs.hasOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER))) {
      resolveLocalConflictsInlineMarkers(ws2ConflictItemsMap, client, config, subargs);
    }
    
    if (unresolvedWorkspaces.size() > 0) {
      throw StatusHelper.conflict(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_CONFILCTS, new String[] {
        config.getContext().getAppName(), 
        SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString(), 
        ResolveCmdOpts.OPT_RESOLVE_PROPOSED.getName(), ResolveCmdOpts.OPT_RESOLVE_MERGED.getName() }));
    }
  }
  

  private void resolveAutomatically(Map<ParmsWorkspace, List<ConflictToResolve>> ws2ConflictItemsMap, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine subargs)
    throws FileSystemException
  {
    List<ParmsWorkspace> unresolvedWorkspaces = new ArrayList();
    List<ShareableDTO> deletedContentShareables = new ArrayList();
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    ResolveAutoMergeResultDTO mergeResult;
    for (Map.Entry<ParmsWorkspace, List<ConflictToResolve>> entryMap : ws2ConflictItemsMap.entrySet())
    {
      ParmsAutoMerge pam = new ParmsAutoMerge();
      
      workspace = new ParmsWorkspace();
      workspace.repositoryUrl = getKeyrepositoryUrl;
      workspace.workspaceItemId = getKeyworkspaceItemId;
      itemsToResolve = new ParmsConflictedItemToResolve[((List)entryMap.getValue()).size()];
      
      int count = 0;
      for (ConflictToResolve conflictToResolve : (List)entryMap.getValue()) {
        ParmsConflictedItemToResolve itemToResolve = new ParmsConflictedItemToResolve();
        componentItemId = conflictToResolve.getComponentId();
        versionableItemId = conflictToResolve.getConflictItem().getVersionableItemId();
        
        itemsToResolve[(count++)] = itemToResolve;
      }
      
      missingRequiredChangesDilemmaHandler = "continue";
      
      pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
      pendingChangesDilemmaHandler.pendingChangesInstruction = "continue";
      pendingChangesDilemmaHandler.commitDilemmaHandler = new ParmsCommitDilemmaHandler();
      pendingChangesDilemmaHandler.commitDilemmaHandler.lineDelimiterDilemmaHandler = new ParmsLineDelimiterDilemmaHandler();
      pendingChangesDilemmaHandler.commitDilemmaHandler.lineDelimiterDilemmaHandler.generalLineDelimiterErrorInstruction = "continue";
      pendingChangesDilemmaHandler.commitDilemmaHandler.predecessorContentDeletedInstruction = "no";
      
      mergeResult = null;
      try {
        mergeResult = client.postAutoResolve(pam, null);
      } catch (TeamRepositoryException e) {
        String wsName = getKeyworkspaceItemId;
        try {
          ((ParmsWorkspace)entryMap.getKey()).getWorkspaceConnection(null).getName();
        }
        catch (Exception localException) {}
        
        config.getContext().stderr().println(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.AcceptCmd_CONFLICT_RESOLUTION_FAILED, wsName));
        LoggingHelper.log("com.ibm.team.filesystem.cli.client", e);
      }
      
      if ((mergeResult != null) && (mergeResult.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0)) {
        deletedContentShareables.addAll(mergeResult.getSandboxUpdateDilemma().getDeletedContentShareables());
      }
      
      if ((mergeResult == null) || (mergeResult.getNumberConflictsResolved() != itemsToResolve.length)) {
        unresolvedWorkspaces.add((ParmsWorkspace)entryMap.getKey());
      }
    }
    
    if (deletedContentShareables.size() > 0) {
      SubcommandUtil.showDeletedContent(com.ibm.team.filesystem.cli.client.internal.Messages.AcceptResultDisplayer_DELETED_CONTENT_WHEN_AUTOMERGING, 
        deletedContentShareables, out);
    }
    

    if ((unresolvedWorkspaces.size() > 0) && 
      (subargs.hasOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER)))
    {
      File cfaRootPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
      if (cfaRootPath != null) {
        Object cfaRoot = new Path(cfaRootPath.getAbsolutePath());
        IConflictHandler handler = new InPlaceConflictHandler();
        
        for (ParmsWorkspace ws : unresolvedWorkspaces) {
          handler.handleConflicts((IPath)cfaRoot, ws, client, config);
        }
        
        out.println(com.ibm.team.filesystem.cli.client.internal.Messages.Conflicts_InPlaceMarkers_Help);
      }
    }
    
    if (unresolvedWorkspaces.size() > 0) {
      throw StatusHelper.conflict(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_CONFILCTS, new String[] {
        config.getContext().getAppName(), 
        SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString(), 
        ResolveCmdOpts.OPT_RESOLVE_PROPOSED.getName(), ResolveCmdOpts.OPT_RESOLVE_MERGED.getName() }));
    }
  }
  

  public static void resolveExternally(ConflictSyncDTO conflictDTO, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine subargs, String externalCompareTool)
    throws FileSystemException
  {
    if ((!conflictDTO.isContentConflict()) && (conflictDTO.isPropertyConflict())) {
      throw StatusHelper.inappropriateArgument(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_CANNOT_EXTERNAL_COMPARE_PROPERTY);
    }
    

    Object element = SyncViewDTOUtil.find(ComponentSyncModel.getInstance(), 
      conflictDTO.getId());
    
    if (!(element instanceof IConflictItem)) {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_PATH_NOT_IN_CONFLICT, 
        conflictDTO.getName()));
    }
    IConflictItem conflictItem = (IConflictItem)element;
    


    AbstractOpenInExternalCompareOperation externalCompareOperation = 
      new OpenConflictInExternalCompareOperation(externalCompareTool, conflictItem, null, true);
    try
    {
      externalCompareOperation.setEnvironment(config.getContext().environment());
      externalCompareOperation.run(null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.failure(com.ibm.team.filesystem.cli.client.internal.Messages.DiffCmd_ERROR_EXTERNAL_COMPARE, e);
    }
    
    ISubcommandDefinition resolveDefn = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
    config.getContext().stdout().println(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_COMPARE_FOLLOWUP_ACTION, 
      new String[] { config.getContext().getAppName(), 
      SubcommandUtil.getExecutionString(resolveDefn).toString(), 
      ResolveCmdOpts.OPT_RESOLVE_MERGED.getName() }));
    
    if (externalCompareOperation.returnValue() != 0) {
      throw StatusHelper.returnValue(externalCompareOperation.returnValue());
    }
  }
  
  public static void resolveExternally(LocalConflictSyncDTO conflictDTO, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine subargs, String externalCompareTool)
    throws FileSystemException
  {
    if ((!conflictDTO.isContentType()) && (conflictDTO.isSetAttributesType())) {
      throw StatusHelper.inappropriateArgument(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_CANNOT_EXTERNAL_COMPARE_PROPERTY);
    }
    

    Object element = SyncViewDTOUtil.find(ComponentSyncModel.getInstance(), 
      conflictDTO.getId());
    

    if (!(element instanceof ILocalConflictItem)) {
      throw StatusHelper.argSyntax(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_PATH_NOT_IN_CONFLICT, 
        conflictDTO.getName()));
    }
    ILocalConflictItem conflictItem = (ILocalConflictItem)element;
    


    AbstractOpenInExternalCompareOperation externalCompareOperation = 
      new OpenConflictInExternalCompareOperation(externalCompareTool, conflictItem, null, true);
    try
    {
      externalCompareOperation.setEnvironment(config.getContext().environment());
      externalCompareOperation.run(null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.failure(com.ibm.team.filesystem.cli.client.internal.Messages.DiffCmd_ERROR_EXTERNAL_COMPARE, e);
    }
    
    ISubcommandDefinition resolveDefn = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
    config.getContext().stdout().println(NLS.bind(com.ibm.team.filesystem.cli.client.internal.Messages.ResolveCmd_COMPARE_LOCAL_CONFLICTS_FOLLOWUP_ACTION, 
      new String[] {
      config.getContext().getAppName(), 
      SubcommandUtil.getExecutionString(resolveDefn).toString(), 
      ResolveCmdOpts.OPT_RESOLVE_LOCAL.getName(), 
      ResolveCmdOpts.OPT_RESOLVE_MERGED.getName() }));
    

    if (externalCompareOperation.returnValue() != 0) {
      throw StatusHelper.returnValue(externalCompareOperation.returnValue());
    }
  }
}
