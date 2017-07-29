package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.IConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers.GapInPlaceConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.flowcommands.conflicthandlers.InPlaceConflictHandler;
import com.ibm.team.filesystem.cli.client.internal.listcommand.ListPortsCmd;
import com.ibm.team.filesystem.cli.client.internal.portcommand.CurrentPortCmd;
import com.ibm.team.filesystem.cli.client.internal.portcommand.PortResolveCmd;
import com.ibm.team.filesystem.cli.client.util.ChangeSetUtil;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.NoSuchAliasException;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.ComponentSyncDTOComparator;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.internal.LoggingHelper;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsAcceptChangeSets;
import com.ibm.team.filesystem.client.rest.parameters.ParmsAutoMerge;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsChangeSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsCommitDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConflictedItemToResolve;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLineDelimiterDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsStructuredResultOptions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceAcceptDetailed;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdate;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdateDilemmaHandler;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.StructuredComponentUpdateReportDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.StructuredUpdateReportDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.WorkspaceUpdateResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.conflict.ResolveAutoMergeResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceFlowEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.patch.CurrentPatchDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.BaselineSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.filesystem.common.workitems.internal.hierarchy.WorkItemHierarchyNodeDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.subcommands.HelpCmd;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.ActiveChangeSetsException;
import com.ibm.team.scm.common.GapException;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.NWayConflictUnsupportedException;
import com.ibm.team.scm.common.PatchInProgressException;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSet;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSetList;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmGapFillingChangeSetsReport;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmGapFillingChangeSetsReportList;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsFillGapChangeSets;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;















public class AcceptCmd
  extends AbstractSubcommand
{
  public AcceptCmd() {}
  
  public void run()
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    ICommandLine subcmd = config.getSubcommandCommandLine();
    
    config.setEnableJSON(subcmd.hasOption(CommonOptions.OPT_JSON));
    boolean automergeDisabled = subcmd.hasOption(AcceptCmdOptions.OPT_DISABLE_AUTOMERGE);
    
    if ((automergeDisabled) && (subcmd.hasOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AcceptCmd2_INVALID_INPLACE_CONFLICT_MARKER_REQUEST, 
        AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER.getName(), AcceptCmdOptions.OPT_DISABLE_AUTOMERGE.getName()));
    }
    
    ITeamRepository repo = null;
    

    ParmsWorkspaceUpdate parms = new ParmsWorkspaceUpdate();
    repo = generateAcceptParms(config, client, subcmd, parms);
    
    if (config.isDryRun()) {
      return;
    }
    ISubcommandDefinition defnTemp;
    NWayConflictUnsupportedException nway;
    try
    {
      result = client.postWorkspaceUpdate(parms, null);
    } catch (TeamRepositoryException e) { WorkspaceUpdateResultDTO result;
      if (isActiveChangeSetsException(e)) {
        throw StatusHelper.disallowed(Messages.AcceptCmd2_CANNOT_ACCEPT_ACTIVE_CS);
      }
      
      PermissionDeniedException pde = (PermissionDeniedException)SubcommandUtil.findExceptionByType(PermissionDeniedException.class, e);
      if (pde != null) {
        throw StatusHelper.permissionFailure(pde, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      PatchInProgressException port = (PatchInProgressException)SubcommandUtil.findExceptionByType(PatchInProgressException.class, e);
      if (port != null) {
        ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, 
          HelpCmd.class);
        ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, 
          AcceptCmd.class);
        throw StatusHelper.portsInProgress(port.getLocalizedMessage() + " " + NLS.bind(Messages.AcceptCmd_PORT_IN_PROGRESS_GUIDANCE, new String[] {
          config.getContext().getAppName(), 
          SubcommandUtil.getExecutionString(defnTemp1).toString(), SubcommandUtil.getExecutionString(defnTemp2).toString() }));
      }
      
      GapException gap = (GapException)SubcommandUtil.findExceptionByType(GapException.class, e);
      if (gap != null) {
        String commandName = SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString();
        defnTemp = SubcommandUtil.getClassSubCommandDefn(config, HelpCmd.class);
        throw StatusHelper.gap(Messages.AcceptCmd_MISSING_CHANGE_SETS + "\n" + NLS.bind(Messages.DeliverCmd2_HINT_ON_GAP, new String[] {
          config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString(), commandName }));
      }
      
      nway = (NWayConflictUnsupportedException)SubcommandUtil.findExceptionByType(NWayConflictUnsupportedException.class, e);
      if (nway != null) {
        throw StatusHelper.nWayConflict(Messages.AcceptCmd2_ACCEPT_WOULD_CAUSE_NWAY_CONFLICT);
      }
      
      throw StatusHelper.wrap(Messages.AcceptCmd2_COULD_NOT_UPDATE, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    
    WorkspaceUpdateResultDTO result;
    if (result.isCancelled())
    {

      if (result.getOutOfSyncShares().size() > 0) {
        AcceptResultDisplayer.showOutOfSync(result.getOutOfSyncShares(), config);
      }
      
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
      
      if (!result.getComponentsWithNWayConflicts().isEmpty()) {
        ComponentDTO cd = (ComponentDTO)result.getComponentsWithNWayConflicts().get(0);
        throw StatusHelper.createException(NLS.bind(Messages.AcceptCmd_0, cd.getName(), AcceptCmdOptions.OPT_NWAY_CONFLICT.getName()), 21, null);
      }
      

      if (!result.getComponentsWithConflictingTargets().isEmpty())
      {
        List<ComponentSyncDTO> componentsWithConflictingTargets = new ArrayList(result.getComponentsWithConflictingTargets());
        Collections.sort(componentsWithConflictingTargets, new PendingChangesUtil.ComponentSyncDTOComparator());
        
        StringBuilder builder = new StringBuilder();
        builder.append(Messages.AcceptCmd_INCOMPATIBLE_COMPONENT_FLOW_TARGETS);
        for (ComponentSyncDTO syncDto : componentsWithConflictingTargets) {
          builder.append(NLS.bind(Messages.AcceptCmd_INCOMPATIBLE_SELECTORS, new String[] {
            AliasUtil.selector(syncDto.getLocalWorkspaceName(), UUID.valueOf(syncDto.getLocalWorkspaceItemId()), syncDto.getLocalRepositoryUrl(), syncDto.isIslocalStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE), 
            AliasUtil.selector(syncDto.getComponentName(), UUID.valueOf(syncDto.getComponentItemId()), syncDto.getTargetIncomingRepositoryUrl(), RepoUtil.ItemType.COMPONENT), 
            AliasUtil.selector(syncDto.getTargetIncomingWorkspaceName(), UUID.valueOf(syncDto.getTargetIncomingWorkspaceItemId()), syncDto.getTargetIncomingRepositoryUrl(), syncDto.isIsTargetIncomingStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE) }));
        }
        
        throw StatusHelper.createException(builder.toString(), 58, null);
      }
      if (!result.getComponentsWithMultipleParticipants().isEmpty())
      {
        List<ComponentSyncDTO> componentsWithMultipleParticpants = new ArrayList(result.getComponentsWithMultipleParticipants());
        Collections.sort(componentsWithMultipleParticpants, new PendingChangesUtil.ComponentSyncDTOComparator());
        
        StringBuilder builder = new StringBuilder();
        builder.append(Messages.AcceptCmd_INCOMPATIBLE_COMPONENT_MULTIPLE_PARTICPANT);
        for (ComponentSyncDTO syncDto : componentsWithMultipleParticpants) {
          builder.append(NLS.bind(Messages.AcceptCmd_INCOMPATIBLE_SELECTORS, new String[] {
            AliasUtil.selector(syncDto.getLocalWorkspaceName(), UUID.valueOf(syncDto.getLocalWorkspaceItemId()), syncDto.getLocalRepositoryUrl(), syncDto.isIslocalStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE), 
            AliasUtil.selector(syncDto.getComponentName(), UUID.valueOf(syncDto.getComponentItemId()), syncDto.getTargetIncomingRepositoryUrl(), RepoUtil.ItemType.COMPONENT), 
            AliasUtil.selector(syncDto.getTargetIncomingWorkspaceName(), UUID.valueOf(syncDto.getTargetIncomingWorkspaceItemId()), syncDto.getTargetIncomingRepositoryUrl(), syncDto.isIsTargetIncomingStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE) }));
        }
        
        throw StatusHelper.createException(builder.toString(), 59, null);
      }
    }
    

    List<ShareableDTO> deletedContentShareables = new ArrayList();
    List<String> unresolvedWorkspaces = autoresolve(client, result, automergeDisabled, deletedContentShareables, config);
    
    boolean verbose = subcmd.hasOption(CommonOptions.OPT_VERBOSE);
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    if (!config.isJSONEnabled()) {
      out.println(Messages.AcceptCmd_ACCEPT_MESSAGE);
    }
    
    AcceptResultDisplayer.showResult(client, repo, isFlowingComponents(config), parms, result, 
      deletedContentShareables, verbose, config);
    
    if ((unresolvedWorkspaces.size() == 0) && (result.getStructuredResult().size() == 0)) {
      StatusHelper.workspaceUnchanged();
    }
    

    if (((unresolvedWorkspaces.size() > 0) || (hasPorts(result))) && 
      (subcmd.hasOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER))) {
      markInPlaceConflicts(config, result, client, verbose);
      out.println(Messages.Conflicts_InPlaceMarkers_Help);
    }
    
    ISubcommandDefinition defnTemp3;
    if (unresolvedWorkspaces.size() > 0)
    {
      out.println(Messages.AcceptCmd_CONFLICT_MESSAGE);
      
      for (String workspace : unresolvedWorkspaces) {
        out.indent().println(workspace);
      }
      
      ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
      ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, ConflictsCmd.class);
      defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, StatusCmd.class);
      
      out.println(NLS.bind(Messages.AcceptCmd_CONFLICT_GUIDANCE, new String[] {
        config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp1).toString(), SubcommandUtil.getExecutionString(defnTemp2).toString(), 
        SubcommandUtil.getExecutionString(defnTemp3).toString() }));
      
      throw StatusHelper.conflict(null);
    }
    

    if (result.isSetEclipseReadFailureMessage()) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      for (Object nextMsg : result.getEclipseReadFailureMessage()) {
        err.println((String)nextMsg);
      }
    }
    
    if (hasPorts(result))
    {
      printPorts(result, out);
    }
    
    if (!subcmd.hasOption(CommonOptions.OPT_JSON)) {
      out.println(Messages.AcceptCmd_SUCCESS);
    }
  }
  
  private void printPorts(WorkspaceUpdateResultDTO result, IndentingPrintStream out)
  {
    out.println(Messages.AcceptCmd_PORT_MESSAGE);
    Iterator localIterator2;
    for (Iterator localIterator1 = result.getStructuredResult().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      StructuredUpdateReportDTO wsReport = (StructuredUpdateReportDTO)localIterator1.next();
      localIterator2 = wsReport.getComponents().iterator(); continue;StructuredComponentUpdateReportDTO compReport = (StructuredComponentUpdateReportDTO)localIterator2.next();
      if (compReport.getCurrentPatch() != null) {
        out.indent().println(NLS.bind(Messages.PropertyListCmd_KeyValue, 
          wsReport.getWorkspaceName(), compReport.getComponentName()));
      }
    }
    

    ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, 
      PortResolveCmd.class);
    ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, 
      CurrentPortCmd.class);
    ISubcommandDefinition defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, 
      ListPortsCmd.class);
    ISubcommandDefinition defnTemp4 = SubcommandUtil.getClassSubCommandDefn(config, 
      StatusCmd.class);
    
    out.println(NLS.bind(Messages.AcceptCmd_PORT_GUIDANCE, new String[] {
      config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp1).toString(), 
      SubcommandUtil.getExecutionString(defnTemp2).toString(), 
      SubcommandUtil.getExecutionString(defnTemp3).toString(), 
      SubcommandUtil.getExecutionString(defnTemp4).toString() }));
  }
  


  public static void markInPlaceConflicts(IScmClientConfiguration config, WorkspaceUpdateResultDTO updateResult, IFilesystemRestClient client, boolean verbose)
    throws FileSystemException
  {
    File cfaRootPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
    
    if (cfaRootPath != null) {
      IPath cfaRoot = new Path(cfaRootPath.getAbsolutePath());
      
      List<StructuredUpdateReportDTO> workspaces = updateResult.getStructuredResult();
      Iterator localIterator2;
      for (Iterator localIterator1 = workspaces.iterator(); localIterator1.hasNext(); 
          











          localIterator2.hasNext())
      {
        StructuredUpdateReportDTO ws = (StructuredUpdateReportDTO)localIterator1.next();
        
        ParmsWorkspace conflictWs = new ParmsWorkspace(ws.getRepositoryUrl(), ws.getWorkspaceItemId());
        IConflictHandler handler = new InPlaceConflictHandler();
        handler.configureVerbose(verbose);
        
        if (ws.isHasConflicts())
        {
          handler.handleConflicts(cfaRoot, conflictWs, client, config);
        }
        
        handler = new GapInPlaceConflictHandler();
        List<StructuredComponentUpdateReportDTO> components = ws.getComponents();
        localIterator2 = components.iterator(); continue;StructuredComponentUpdateReportDTO comp = (StructuredComponentUpdateReportDTO)localIterator2.next();
        CurrentPatchDTO currentPort = comp.getCurrentPatch();
        if (currentPort != null)
        {
          handler.handleConflicts(cfaRoot, conflictWs, currentPort, client, config);
        }
      }
    }
  }
  











  public static List<String> autoresolve(IFilesystemRestClient client, WorkspaceUpdateResultDTO updateResult, boolean automergeDisabled, List<ShareableDTO> deletedContentShareables, IScmClientConfiguration config)
  {
    List<String> unresolvedWorkspaces = new ArrayList();
    
    if (automergeDisabled) {
      return hasConflicts(updateResult);
    }
    
    List<StructuredUpdateReportDTO> workspaces = updateResult.getStructuredResult();
    for (StructuredUpdateReportDTO ws : workspaces) {
      if (ws.isHasConflicts())
      {



        LinkedList<ParmsConflictedItemToResolve> conflictedParms = new LinkedList();
        Iterator localIterator3;
        for (Iterator localIterator2 = ws.getComponents().iterator(); localIterator2.hasNext(); 
            
            localIterator3.hasNext())
        {
          StructuredComponentUpdateReportDTO comp = (StructuredComponentUpdateReportDTO)localIterator2.next();
          
          localIterator3 = comp.getConflictedItemIds().iterator(); continue;String conflictItemId = (String)localIterator3.next();
          conflictedParms.add(new ParmsConflictedItemToResolve(comp.getComponentItemId(), conflictItemId));
        }
        

        if (conflictedParms.size() > 0)
        {
          ParmsAutoMerge pam = new ParmsAutoMerge();
          
          workspace = new ParmsWorkspace();
          workspace.repositoryUrl = ws.getRepositoryUrl();
          workspace.workspaceItemId = ws.getWorkspaceItemId();
          itemsToResolve = ((ParmsConflictedItemToResolve[])conflictedParms.toArray(new ParmsConflictedItemToResolve[conflictedParms.size()]));
          
          missingRequiredChangesDilemmaHandler = "continue";
          
          pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
          pendingChangesDilemmaHandler.pendingChangesInstruction = "continue";
          pendingChangesDilemmaHandler.commitDilemmaHandler = new ParmsCommitDilemmaHandler();
          pendingChangesDilemmaHandler.commitDilemmaHandler.lineDelimiterDilemmaHandler = new ParmsLineDelimiterDilemmaHandler();
          pendingChangesDilemmaHandler.commitDilemmaHandler.lineDelimiterDilemmaHandler.generalLineDelimiterErrorInstruction = "continue";
          pendingChangesDilemmaHandler.commitDilemmaHandler.predecessorContentDeletedInstruction = "no";
          
          ResolveAutoMergeResultDTO mergeResult = null;
          try {
            mergeResult = client.postAutoResolve(pam, null);
          } catch (TeamRepositoryException e) {
            config.getContext().stderr().println(NLS.bind(Messages.AcceptCmd_CONFLICT_RESOLUTION_FAILED, ws.getWorkspaceName()));
            LoggingHelper.log("com.ibm.team.filesystem.cli.client", e);
          }
          
          if ((mergeResult != null) && (mergeResult.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0)) {
            deletedContentShareables.addAll(mergeResult.getSandboxUpdateDilemma().getDeletedContentShareables());
          }
          
          if ((mergeResult == null) || (mergeResult.getNumberConflictsResolved() != itemsToResolve.length)) {
            unresolvedWorkspaces.add(ws.getWorkspaceName());
          }
        }
      }
    }
    return unresolvedWorkspaces;
  }
  
  public static List<String> hasConflicts(WorkspaceUpdateResultDTO result)
  {
    List<String> unresolvedWorkspaces = new ArrayList();
    for (StructuredUpdateReportDTO wsReport : result.getStructuredResult()) {
      if (wsReport.isHasConflicts()) {
        unresolvedWorkspaces.add(wsReport.getWorkspaceName());
      }
    }
    
    return unresolvedWorkspaces;
  }
  
  private boolean hasPorts(WorkspaceUpdateResultDTO result) {
    Iterator localIterator2;
    for (Iterator localIterator1 = result.getStructuredResult().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      StructuredUpdateReportDTO wsReport = (StructuredUpdateReportDTO)localIterator1.next();
      localIterator2 = wsReport.getComponents().iterator(); continue;StructuredComponentUpdateReportDTO compReport = (StructuredComponentUpdateReportDTO)localIterator2.next();
      if (compReport.getCurrentPatch() != null) {
        return true;
      }
    }
    

    return false;
  }
  




  private boolean isActiveChangeSetsException(TeamRepositoryException e)
  {
    Throwable cause = e;
    
    int tries = 0;
    while (cause != null) {
      if ((cause instanceof ActiveChangeSetsException)) {
        return true;
      }
      
      cause = cause.getCause();
      
      if (tries++ > 100)
      {
        return false;
      }
    }
    
    return false;
  }
  
  static enum Mode { UNSET,  WS,  CS,  BASELINE,  COMPONENT,  WI;
  }
  
  private boolean isFlowingComponents(IScmClientConfiguration config) { return config.getSubcommandCommandLine().hasOption(AcceptCmdOptions.OPT_FLOW_COMPONENTS); }
  

  private ITeamRepository generateAcceptParms(IScmClientConfiguration config, IFilesystemRestClient client, ICommandLine subcmd, ParmsWorkspaceUpdate parms)
    throws FileSystemException
  {
    ITeamRepository repo = null;
    
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    

    Mode mode = Mode.UNSET;
    
    int modeCount = 0;
    if (subcmd.hasOption(AcceptCmdOptions.OPT_MODE_BASELINES)) {
      mode = Mode.BASELINE;
      modeCount++;
    }
    
    if (subcmd.hasOption(AcceptCmdOptions.OPT_MODE_COMPONENTS)) {
      mode = Mode.COMPONENT;
      modeCount++;
    }
    
    if (subcmd.hasOption(AcceptCmdOptions.OPT_MODE_CHANGESETS)) {
      mode = Mode.CS;
      modeCount++;
    }
    
    if (subcmd.hasOption(AcceptCmdOptions.OPT_MODE_WORKITEMS)) {
      mode = Mode.WI;
      modeCount++;
    }
    
    if (modeCount > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AcceptCmd2_USE_SINGLE_MODE_ARGUMENT, 
        new String[] { AcceptCmdOptions.OPT_MODE_BASELINES.getName(), AcceptCmdOptions.OPT_MODE_COMPONENTS.getName(), 
        AcceptCmdOptions.OPT_MODE_CHANGESETS.getName(), AcceptCmdOptions.OPT_MODE_WORKITEMS.getName() }));
    }
    

    if ((subcmd.hasOption(AcceptCmdOptions.OPT_MODE_WORKITEMS)) && (subcmd.hasOption(CommonOptions.OPT_STREAM_SOURCE_SELECTOR))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_2_ARGUMENTS, 
        AcceptCmdOptions.OPT_MODE_WORKITEMS.getName(), CommonOptions.OPT_STREAM_SOURCE_SELECTOR.getName()));
    }
    

    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "cancel";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "cancel";
    
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (subcmd.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    

    structuredResultOptions = new ParmsStructuredResultOptions();
    workspaceUpdateDilemmaHandler = new ParmsWorkspaceUpdateDilemmaHandler();
    workspaceUpdateDilemmaHandler.disconnectedComponentsDirection = "continue";
    
    String compStrategy = "no";
    if (isFlowingComponents(config)) {
      compStrategy = "continue";
    }
    workspaceUpdateDilemmaHandler.componentFlowDirection = compStrategy;
    workspaceUpdateDilemmaHandler.componentReplacementCandidatesDirection = compStrategy;
    if (subcmd.hasOption(AcceptCmdOptions.OPT_NWAY_CONFLICT)) {
      workspaceUpdateDilemmaHandler.nWayConflictDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.nWayConflictDirection = "cancel";
    }
    
    if (subcmd.hasOption(CommonOptions.OPT_MULTIPLE_PARTICIPANTS)) {
      workspaceUpdateDilemmaHandler.multipleParticipantsDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.multipleParticipantsDirection = "cancel";
    }
    

    if (subcmd.hasOption(AcceptCmdOptions.OPT_ACCEPT_WITH_PORTING)) {
      workspaceUpdateDilemmaHandler.gapDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.gapDirection = "fail";
    }
    
    if (subcmd.hasOption(AcceptCmdOptions.OPT_ACCEPT_WITH_PORTING)) {
      workspaceUpdateDilemmaHandler.portInProgressDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.portInProgressDirection = "fail";
    }
    

    List<ParmsWorkspace> targetWs = new ArrayList(1);
    
    if (!subcmd.hasOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR)) {
      repo = RepoUtil.loginUrlArgAnc(config, client);
      
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, 
        config);
      for (ISandboxWorkspace wsInSandbox : wsInSandboxList) {
        if (wsInSandbox.getRepositoryId().equals(repo.getId().getUuidValue())) {
          targetWs.add(new ParmsWorkspace(repo.getRepositoryURI(), 
            wsInSandbox.getWorkspaceItemId()));
        }
      }
      if (targetWs.size() == 0) {
        throw StatusHelper.misconfiguredLocalFS(Messages.Common_WS_NOT_FOUND);
      }
    }
    else {
      IScmCommandLineArgument targetSelector = ScmCommandLineArgument.create(subcmd.getOptionValue(CommonOptions.OPT_STREAM_TARGET_SELECTOR), config);
      SubcommandUtil.validateArgument(targetSelector, RepoUtil.ItemType.WORKSPACE);
      repo = RepoUtil.loginUrlArgAncestor(config, client, targetSelector);
      
      IWorkspace ws = RepoUtil.getWorkspace(targetSelector.getItemSelector(), true, false, repo, config);
      
      ParmsWorkspace parm = new ParmsWorkspace();
      repositoryUrl = repo.getRepositoryURI();
      workspaceItemId = ws.getItemId().getUuidValue();
      
      targetWs.add(parm);
    }
    

    ParmsWorkspace sourceWs = null;
    ITeamRepository srcRepo = null;
    if (subcmd.hasOption(CommonOptions.OPT_STREAM_SOURCE_SELECTOR)) {
      Object sourceSelector = ScmCommandLineArgument.create(subcmd.getOptionValue(CommonOptions.OPT_STREAM_SOURCE_SELECTOR), config);
      SubcommandUtil.validateArgument((IScmCommandLineArgument)sourceSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      
      srcRepo = RepoUtil.loginUrlArgAncestor(config, client, (IScmCommandLineArgument)sourceSelector);
      IWorkspace ws = RepoUtil.getWorkspace(((IScmCommandLineArgument)sourceSelector).getItemSelector(), true, true, srcRepo, config);
      
      sourceWs = new ParmsWorkspace();
      repositoryUrl = srcRepo.getRepositoryURI();
      workspaceItemId = ws.getItemId().getUuidValue();
    }
    
    Object argSelectors = Collections.EMPTY_LIST;
    List<String> selectors = Collections.EMPTY_LIST;
    if (subcmd.hasOption(AcceptCmdOptions.OPT_SELECTORS))
    {
      argSelectors = ScmCommandLineArgument.createList(subcmd.getOptionValues(AcceptCmdOptions.OPT_SELECTORS), config, mode != Mode.WI);
      selectors = RepoUtil.getSelectors((List)argSelectors);
    }
    



    if ((mode == Mode.UNSET) && (selectors.size() == 0))
    {


      List<ParmsWorkspaceAcceptDetailed> updates = new ArrayList(targetWs.size());
      
      RepoUtil.loginToWsCollabRepos(targetWs, client, config, true);
      

      for (ParmsWorkspace target : targetWs) {
        updates.addAll(generateDefaultFlowAcceptParms(sourceWs, target, client, config));
      }
      
      workspaceAcceptDetailed = ((ParmsWorkspaceAcceptDetailed[])updates.toArray(new ParmsWorkspaceAcceptDetailed[updates.size()]));
    } else if (((mode == Mode.UNSET) || (mode == Mode.CS)) && (selectors.size() > 0))
    {




      if (targetWs.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.AcceptCmd2_TOO_MANY_WORKSPACES, subcmd.getDefinition().getOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR).getName()));
      }
      
      SubcommandUtil.validateArgument((List)argSelectors, RepoUtil.ItemType.CHANGESET);
      
      if (sourceWs != null)
      {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, (List)argSelectors, srcRepo, config);
      }
      

      ParmsAcceptChangeSets update = new ParmsAcceptChangeSets();
      
      workspace = ((ParmsWorkspace)targetWs.get(0));
      
      changeSets = normalizeCsUuidAndAlias(sourceWs, (ParmsWorkspace)targetWs.get(0), (List)argSelectors, config, client);
      
      acceptChangeSets = new ParmsAcceptChangeSets[] { update };
    } else if (mode == Mode.COMPONENT)
    {




      if (selectors.size() < 1) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.AcceptCmd2_NO_COMPONENTS_SPECIFIED, AcceptCmdOptions.OPT_MODE_COMPONENTS.getName()));
      }
      
      if (targetWs.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.AcceptCmd2_TOO_MANY_WORKSPACES_FOR_COMPONENT, subcmd.getDefinition().getOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR).getName()));
      }
      
      SubcommandUtil.validateArgument((List)argSelectors, RepoUtil.ItemType.COMPONENT);
      
      if (sourceWs != null)
      {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, (List)argSelectors, srcRepo, config);
      }
      

      workspaceAcceptDetailed = generateComponentFlowAcceptParms(config, (ParmsWorkspace)targetWs.get(0), sourceWs, selectors, client);
    } else if (mode == Mode.BASELINE)
    {




      if (selectors.size() < 1) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.AcceptCmd2_NO_BASELINES_SPECIFIED, AcceptCmdOptions.OPT_MODE_BASELINES.getName()));
      }
      
      if (targetWs.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.AcceptCmd2_TOO_MANY_WORKSPACES_FOR_BASELINE, subcmd.getDefinition().getOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR).getName()));
      }
      
      SubcommandUtil.validateArgument((List)argSelectors, RepoUtil.ItemType.BASELINE);
      
      if (sourceWs != null)
      {
        RepoUtil.validateItemRepos(RepoUtil.ItemType.BASELINE, (List)argSelectors, srcRepo, config);
      }
      

      workspaceAcceptDetailed = generateBaselineAcceptParms(config, (ParmsWorkspace)targetWs.get(0), sourceWs, selectors, client);
    } else if (mode == Mode.WI)
    {
      if (selectors.size() < 1) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.AcceptCmd2_NO_WORKITEMS_SPECIFIED, AcceptCmdOptions.OPT_MODE_WORKITEMS.getName()));
      }
      
      if (targetWs.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.AcceptCmd2_TOO_MANY_WORKSPACES_FOR_WORKITEM, subcmd.getDefinition().getOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR).getName()));
      }
      
      SubcommandUtil.validateArgument((List)argSelectors, RepoUtil.ItemType.WORKITEM);
      

      ParmsAcceptChangeSets update = new ParmsAcceptChangeSets();
      workspace = ((ParmsWorkspace)targetWs.get(0));
      changeSets = getChangeSetsFromWorkitem((List)argSelectors, (ParmsWorkspace)targetWs.get(0), client, 
        config, repo, subcmd);
      acceptChangeSets = new ParmsAcceptChangeSets[] { update };
    } else {
      throw StatusHelper.internalError(Messages.AcceptCmd2_UNEXPECTED_CASE);
    }
    


    if (config.getSubcommandCommandLine().hasOption(AcceptCmdOptions.OPT_ACCEPT_MISSING_CHANGESETS)) {
      if (((mode != Mode.UNSET) && (mode != Mode.CS)) || (selectors.size() <= 0))
      {
        throw StatusHelper.argSyntax(Messages.AcceptCmd_UNEXPECTED_MISSING_CHANGESET);
      }
      

      List<ParmsChangeSet> changes = fetchGapFillingChangeSets(repo, (ParmsWorkspace)targetWs.get(0), (List)argSelectors);
      

      if ((changes != null) && (!changes.isEmpty())) {
        ParmsAcceptChangeSets update = new ParmsAcceptChangeSets();
        workspace = ((ParmsWorkspace)targetWs.get(0));
        changeSets = ((ParmsChangeSet[])changes.toArray(new ParmsChangeSet[changes.size()]));
        ParmsAcceptChangeSets[] b = new ParmsAcceptChangeSets[acceptChangeSets.length + 1];
        System.arraycopy(acceptChangeSets, 0, b, 0, acceptChangeSets.length);
        b[acceptChangeSets.length] = update;
        acceptChangeSets = b;
      }
    }
    
    return repo;
  }
  

  private ParmsChangeSet[] getChangeSetsFromWorkitem(List<IScmCommandLineArgument> argSelectors, ParmsWorkspace ws, IFilesystemRestClient client, IScmClientConfiguration config, ITeamRepository repo, ICommandLine subcmd)
    throws FileSystemException
  {
    ChangeSetUtil csUtil = new ChangeSetUtil();
    csUtil.validateRepoAndLogin(argSelectors, client, config, subcmd, repo == null);
    

    List<WorkItemHierarchyNodeDTO> wiHierarchyList = new ArrayList();
    Map<String, ChangeSetSyncDTO> csList = csUtil.getChangeSetsFromWorkitem(argSelectors, repo, client, config, 
      false, wiHierarchyList);
    
    if ((csList == null) || (csList.size() == 0)) {
      config.getContext().stdout().println(Messages.ChangesetLocateCmd_CS_NOT_FOUND_FOR_WI);
      StatusHelper.workspaceUnchanged();
    }
    

    WorkspaceDetailsDTO wsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    List<String> compIds = new ArrayList(wsDetails.getComponents().size());
    for (WorkspaceComponentDTO compDTO : wsDetails.getComponents()) {
      compIds.add(compDTO.getItemId());
    }
    

    List<ParmsChangeSet> parmsCsList = new ArrayList(csList.size());
    for (ChangeSetSyncDTO csDTO : csList.values())
    {
      if (compIds.contains(csDTO.getComponentItemId())) {
        ParmsChangeSet parmsCs = new ParmsChangeSet();
        changeSetItemId = csDTO.getChangeSetItemId();
        repositoryUrl = csDTO.getRepositoryUrl();
        
        parmsCsList.add(parmsCs);
      }
    }
    
    return (ParmsChangeSet[])parmsCsList.toArray(new ParmsChangeSet[parmsCsList.size()]);
  }
  
  public static List<ParmsChangeSet> fetchGapFillingChangeSets(ITeamRepository repo, ParmsWorkspace ws, List<IScmCommandLineArgument> selectors) throws FileSystemException {
    List<ParmsChangeSet> changesParm = new ArrayList();
    


    List<String> toExclude = new ArrayList();
    String repositoryURL = null;
    for (IScmCommandLineArgument selector : selectors) {
      IUuidAliasRegistry.IUuidAlias alias = selector.getAlias();
      if (alias == null)
      {
        throw new RuntimeException();
      }
      ParmsChangeSet parmsCs = new ParmsChangeSet();
      changeSetItemId = alias.getUuid().getUuidValue();
      toExclude.add(changeSetItemId);
      repositoryURL = repositoryURL == null ? alias.getRepositoryUrl() : alias.getRepositoryUrl() == null ? repositoryUrl : repositoryURL;
      repositoryUrl = repositoryURL;
      changesParm.add(parmsCs);
    }
    


    IScmRichClientRestService.ParmsFillGapChangeSets fillGapsParms = null;
    ScmGapFillingChangeSetsReportList reports = null;
    try {
      fillGapsParms = generateFillGapsChangeSetsParms(ws, changesParm);
      IScmRichClientRestService scmRichService = (IScmRichClientRestService)((IClientLibraryContext)repo)
        .getServiceInterface(IScmRichClientRestService.class);
      
      reports = scmRichService.getChangeSetsFillingGap(fillGapsParms);
    } catch (TeamRepositoryException e) {
      throw new FileSystemException(e);
    }
    

    List<ScmChangeSet> changes = new ArrayList();
    for (ScmGapFillingChangeSetsReport report : reports.getReports()) {
      changes.addAll(report.getChanges().getChangeSets());
    }
    

    if (changes.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    

    List<ParmsChangeSet> result = new ArrayList();
    for (ScmChangeSet change : changes) {
      String itemId = change.getItemId();
      
      if (!toExclude.contains(itemId))
      {

        ParmsChangeSet parmsCs = new ParmsChangeSet();
        changeSetItemId = itemId;
        repositoryUrl = repositoryURL;
        result.add(parmsCs);
      }
    }
    
    if (result.isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    return result;
  }
  


  private static IScmRichClientRestService.ParmsFillGapChangeSets generateFillGapsChangeSetsParms(ParmsWorkspace ws, List<ParmsChangeSet> changesets)
    throws TeamRepositoryException
  {
    IScmRichClientRestService.ParmsFillGapChangeSets result = new IScmRichClientRestService.ParmsFillGapChangeSets();
    workspaceItemId = ws.getWorkspaceHandle().getItemId().getUuidValue();
    List<String> ids = new ArrayList();
    for (ParmsChangeSet cs : changesets) {
      ids.add(cs.getChangeSetHandle().getItemId().getUuidValue());
    }
    changeSetItemIds = ((String[])ids.toArray(new String[ids.size()]));
    return result;
  }
  
  private static ParmsChangeSet[] normalizeCsUuidAndAlias(ParmsWorkspace srcWs, ParmsWorkspace ws, List<IScmCommandLineArgument> csSelectors, IScmClientConfiguration config, IFilesystemRestClient client)
    throws FileSystemException
  {
    List<ParmsChangeSet> normalized = new ArrayList();
    
    for (int i = 0; i < csSelectors.size(); i++) {
      IScmCommandLineArgument selector = (IScmCommandLineArgument)csSelectors.get(i);
      
      IUuidAliasRegistry.IUuidAlias uuid = RepoUtil.lookupUuidAndAlias(selector.getItemSelector());
      
      if (uuid == null) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.AcceptCmd2_CANNOT_FIND_CHANGE_SET, selector.getItemSelector()));
      }
      
      if (srcWs == null) {
        srcWs = ws;
      }
      
      ITeamRepository csRepo = RepoUtil.getSharedRepository(repositoryUrl, true);
      
      if (selector.isRepoExplicit()) {
        csRepo = RepoUtil.loginUrlArgAncestor(config, client, selector);
      } else if (uuid.getRepositoryUrl() != null) {
        csRepo = RepoUtil.login(config, client, config.getConnectionInfo(uuid.getRepositoryUrl(), null, false, true));
      }
      

      IChangeSet changeSet = RepoUtil.findChangeSet(selector.getItemSelector(), csRepo, config);
      ParmsChangeSet parmsCs = new ParmsChangeSet();
      changeSetItemId = changeSet.getItemId().getUuidValue();
      repositoryUrl = csRepo.getRepositoryURI();
      normalized.add(parmsCs);
    }
    

    ParmsChangeSet[] str = new ParmsChangeSet[normalized.size()];
    return (ParmsChangeSet[])normalized.toArray(str);
  }
  








  private List<ParmsWorkspaceAcceptDetailed> generateDefaultFlowAcceptParms(ParmsWorkspace sourceWs, ParmsWorkspace target, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    HashMap<ParmsWorkspace, List<String>> componentFlows = new HashMap();
    List<WorkspaceDetailsDTO> tgtWsDetailsList;
    List<String> components; WorkspaceSyncDTO wsSync; if ((sourceWs != null) && (!isCurrentIncomingFlow(sourceWs, target, client))) {
      List<WorkspaceDetailsDTO> srcWsDetailsList = RepoUtil.getWorkspaceDetails(Collections.singletonList(
        sourceWs), client, config);
      WorkspaceDetailsDTO srcWsDetails = (WorkspaceDetailsDTO)srcWsDetailsList.get(0);
      List<WorkspaceComponentDTO> srcCompDTOList = srcWsDetails.getComponents();
      
      tgtWsDetailsList = RepoUtil.getWorkspaceDetails(Collections.singletonList(target), 
        client, config);
      List<WorkspaceComponentDTO> tgtCompDTOList = ((WorkspaceDetailsDTO)tgtWsDetailsList.get(0)).getComponents();
      

      components = new ArrayList();
      
      for (WorkspaceComponentDTO tgtCompDTO : tgtCompDTOList) {
        for (WorkspaceComponentDTO srcCompDTO : srcCompDTOList) {
          if (tgtCompDTO.getItemId().equals(srcCompDTO.getItemId())) {
            components.add(tgtCompDTO.getItemId());
            break;
          }
        }
      }
      
      if (components.size() > 0) {
        ParmsWorkspace ws = new ParmsWorkspace(srcWsDetails.getRepositoryURL(), srcWsDetails.getItemId());
        componentFlows.put(ws, components);
      }
    } else {
      SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(target), true, client, config);
      
      List<ParmsWorkspace> wsList = new ArrayList();
      for (tgtWsDetailsList = syncView.getWorkspaces().iterator(); tgtWsDetailsList.hasNext();) { wsSync = (WorkspaceSyncDTO)tgtWsDetailsList.next();
        
        if (wsSync.getWorkspaceItemId().equals(workspaceItemId))
        {
          for (ComponentSyncDTO compSync : wsSync.getComponents())
          {
            ParmsWorkspace ws = new ParmsWorkspace(compSync.getTargetIncomingRepositoryUrl(), compSync.getTargetIncomingWorkspaceItemId());
            ParmsWorkspace wsMatched = findWorkspace(wsList, ws);
            
            List<String> components = null;
            if (wsMatched != null) {
              components = (List)componentFlows.get(wsMatched);
            }
            
            if (components == null) {
              components = new ArrayList();
              
              componentFlows.put(ws, components);
              wsList.add(ws);
            }
            
            components.add(compSync.getComponentItemId());
          }
          
          break;
        }
      }
    }
    

    ArrayList<ParmsWorkspaceAcceptDetailed> parms = new ArrayList(componentFlows.size());
    for (Map.Entry<ParmsWorkspace, List<String>> entry : componentFlows.entrySet()) {
      ParmsWorkspaceAcceptDetailed detailed = new ParmsWorkspaceAcceptDetailed();
      
      workspace = target;
      sourceWorkspace = ((ParmsWorkspace)entry.getKey());
      componentItemIds = ((String[])((List)entry.getValue()).toArray(new String[((List)entry.getValue()).size()]));
      
      parms.add(detailed);
    }
    
    return parms;
  }
  
  private boolean isCurrentIncomingFlow(ParmsWorkspace sourceWs, ParmsWorkspace target, IFilesystemRestClient client) throws FileSystemException {
    List<WorkspaceDetailsDTO> workspaceDetails = RepoUtil.getWorkspaceDetails(Collections.singletonList(target), client, config);
    if (!workspaceDetails.isEmpty()) {
      WorkspaceDetailsDTO details = (WorkspaceDetailsDTO)workspaceDetails.get(0);
      if (details != null) {
        for (Object o : details.getFlowEntries()) {
          WorkspaceFlowEntryDTO entry = (WorkspaceFlowEntryDTO)o;
          if (entry.isCurrentIncomingFlow()) {
            return entry.getWorkspaceItemId().equals(workspaceItemId);
          }
        }
      }
    }
    return false;
  }
  





  private ParmsWorkspaceAcceptDetailed[] generateComponentFlowAcceptParms(IScmClientConfiguration config, ParmsWorkspace targetWs, ParmsWorkspace sourceWs, List<String> selectors, IFilesystemRestClient client)
    throws FileSystemException
  {
    ArrayList<ParmsWorkspaceAcceptDetailed> parms = new ArrayList(5);
    
    HashSet<String> selectorSet = new HashSet(selectors);
    
    Map<ParmsWorkspace, List<String>> componentFlows = new HashMap();
    String wsName = null;
    
    boolean sourceWorkspaceExplicit = sourceWs != null;
    List<WorkspaceDetailsDTO> tgtWsDetailsList; Map<String, String> componentId2selector; WorkspaceSyncDTO wsSync; if (sourceWs != null) {
      List<WorkspaceDetailsDTO> srcWsDetailsList = RepoUtil.getWorkspaceDetails(Collections.singletonList(sourceWs), 
        client, config);
      WorkspaceDetailsDTO srcWsDetails = (WorkspaceDetailsDTO)srcWsDetailsList.get(0);
      List<WorkspaceComponentDTO> srcCompDTOList = srcWsDetails.getComponents();
      wsName = srcWsDetails.getName();
      
      tgtWsDetailsList = RepoUtil.getWorkspaceDetails(Collections.singletonList(targetWs), 
        client, config);
      List<WorkspaceComponentDTO> tgtCompDTOList = ((WorkspaceDetailsDTO)tgtWsDetailsList.get(0)).getComponents();
      

      componentId2selector = new HashMap();
      Map<String, String> selector2componentName = new HashMap();
      
      String matchedSelector;
      for (WorkspaceComponentDTO srcCompDTO : srcCompDTOList) {
        matchedSelector = matchesSelector(config, srcCompDTO.getItemId(), srcCompDTO.getName(), 
          selectorSet, repositoryUrl);
        
        if (matchedSelector != null) {
          selectorSet.remove(matchedSelector);
          
          componentId2selector.put(srcCompDTO.getItemId(), matchedSelector);
          selector2componentName.put(matchedSelector, srcCompDTO.getName());
        }
      }
      
      if (selectorSet.size() == 0) {
        wsName = ((WorkspaceDetailsDTO)tgtWsDetailsList.get(0)).getName();
        RepoUtil.ItemType itemType = ((WorkspaceDetailsDTO)tgtWsDetailsList.get(0)).isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE;
        
        boolean found;
        for (String component : componentId2selector.keySet()) {
          found = false;
          for (WorkspaceComponentDTO tgtCompDTO : tgtCompDTOList) {
            if (tgtCompDTO.getItemId().equals(component)) {
              found = true;
              break;
            }
          }
          
          if (!found) {
            selectorSet.add((String)componentId2selector.get(component));
          }
        }
        
        if (selectorSet.size() > 0) {
          IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
          err.println(NLS.bind(Messages.ReplaceComponentsCmd_COMPONENT_NOT_IN_WORKSPACE, 
            AliasUtil.selector(wsName, UUID.valueOf(workspaceItemId), repositoryUrl, 
            itemType)));
          err.indent();
          for (String selector : selectorSet) {
            err.println((CharSequence)selector2componentName.get(selector));
          }
          throw StatusHelper.ambiguousSelector(Messages.CreateBaselineCmd_12);
        }
        

        ParmsWorkspace ws = new ParmsWorkspace(srcWsDetails.getRepositoryURL(), srcWsDetails.getItemId());
        componentFlows.put(ws, new ArrayList(componentId2selector.keySet()));

      }
      


    }
    else
    {


      SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(targetWs), true, client, config);
      

      List<ParmsWorkspace> wsList = new ArrayList();
      for (tgtWsDetailsList = syncView.getWorkspaces().iterator(); tgtWsDetailsList.hasNext();) { wsSync = (WorkspaceSyncDTO)tgtWsDetailsList.next();
        
        if (wsSync.getWorkspaceItemId().equals(workspaceItemId)) {
          wsName = wsSync.getWorkspaceName();
          

          for (ComponentSyncDTO compSync : wsSync.getComponents()) {
            String matchedSelector = matchesSelector(config, compSync.getComponentItemId(), compSync.getComponentName(), selectorSet, wsSync.getRepositoryUrl());
            
            if (matchedSelector != null) {
              selectorSet.remove(matchedSelector);
              
              ParmsWorkspace ws = new ParmsWorkspace(compSync.getTargetIncomingRepositoryUrl(), compSync.getTargetIncomingWorkspaceItemId());
              ParmsWorkspace wsMatched = findWorkspace(wsList, ws);
              
              List<String> components = null;
              if (wsMatched != null) {
                components = (List)componentFlows.get(wsMatched);
              }
              
              if (components == null) {
                components = new ArrayList();
                
                componentFlows.put(ws, components);
                wsList.add(ws);
              }
              
              components.add(compSync.getComponentItemId());
            }
            
            if (selectorSet.size() == 0) {
              break;
            }
          }
          
          break;
        }
      }
    }
    

    if (selectorSet.size() > 0) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      err.println(Messages.AcceptCmd2_MISSING_COMPONENT_SELECTOR_LIST_START);
      err = err.indent();
      for (String componentSelector : selectorSet) {
        err.println(componentSelector);
      }
      throw StatusHelper.ambiguousSelector(NLS.bind(Messages.AcceptCmd2_MISSING_COMPONENT_SELECTOR_MESSAGE, wsName));
    }
    

    boolean hierarchicalAccept = config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_COMPONENT_HIERARCHY);
    for (Map.Entry<ParmsWorkspace, List<String>> entry : componentFlows.entrySet()) {
      ParmsWorkspaceAcceptDetailed detailed = new ParmsWorkspaceAcceptDetailed();
      
      workspace = targetWs;
      sourceWorkspace = ((ParmsWorkspace)entry.getKey());
      componentItemIds = ((String[])((List)entry.getValue()).toArray(new String[((List)entry.getValue()).size()]));
      hierarchicalAccept = hierarchicalAccept;
      sourceWorkspaceExplicit = sourceWorkspaceExplicit;
      
      parms.add(detailed);
    }
    
    return (ParmsWorkspaceAcceptDetailed[])parms.toArray(new ParmsWorkspaceAcceptDetailed[parms.size()]);
  }
  
  ParmsWorkspace findWorkspace(List<ParmsWorkspace> wsList, ParmsWorkspace ws) {
    for (ParmsWorkspace wsInList : wsList) {
      if ((repositoryUrl.equals(repositoryUrl)) && (workspaceItemId.equals(workspaceItemId))) {
        return wsInList;
      }
    }
    
    return null;
  }
  







  private ParmsWorkspaceAcceptDetailed[] generateBaselineAcceptParms(IScmClientConfiguration config, ParmsWorkspace targetWs, ParmsWorkspace sourceWs, List<String> selectors, IFilesystemRestClient client)
    throws FileSystemException
  {
    Set<String> selectorSet = new HashSet(selectors);
    
    Map<ParmsWorkspace, HashSet<String>> ws2Baseline = new HashMap();
    String wsName = null;
    WorkspaceDetailsDTO targetWsDetails;
    HashSet<String> baselines; WorkspaceComponentDTO sourceComp; WorkspaceSyncDTO wsSync; if (sourceWs != null)
    {
      WorkspaceDetailsDTO sourceWsDetails = 
        (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(sourceWs), client, config).get(0);
      List<WorkspaceComponentDTO> sourceWsComps = sourceWsDetails.getComponents();
      ITeamRepository srcRepo = RepoUtil.getSharedRepository(repositoryUrl, true);
      wsName = sourceWsDetails.getName();
      

      targetWsDetails = 
        (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(targetWs), client, config).get(0);
      List<WorkspaceComponentDTO> targetWsComps = targetWsDetails.getComponents();
      

      baselines = new HashSet();
      
      for (WorkspaceComponentDTO targetComp : targetWsComps) {
        for (Iterator localIterator2 = sourceWsComps.iterator(); localIterator2.hasNext();) { sourceComp = (WorkspaceComponentDTO)localIterator2.next();
          if (targetComp.getItemId().equals(sourceComp.getItemId())) {
            int blCount = 0;
            for (String selector : selectors) {
              IBaseline bl = null;
              try {
                bl = RepoUtil.getBaseline(selector, sourceComp.getItemId(), sourceComp.getName(), 
                  srcRepo, client, config);
              } catch (FileSystemException e) {
                if ((!(e instanceof CLIFileSystemClientException)) || 
                  (((CLIFileSystemClientException)e).getStatus().getCode() != 25))
                {

                  throw e;
                }
              }
              if (bl != null) {
                selectorSet.remove(selector);
                baselines.add(bl.getItemId().getUuidValue());
                blCount++;
              }
            }
            
            if (blCount <= 1) break;
            throw StatusHelper.inappropriateArgument(Messages.DeliverCmd_SPECIFY_ONLY_ONE_BASLELINE_PER_COMPONENT);
          }
        }
      }
      


      if (baselines.size() > 0) {
        ParmsWorkspace ws = sourceWs;
        ws2Baseline.put(ws, baselines);
      }
    }
    else {
      SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(targetWs), false, client, config);
      

      List<ParmsWorkspace> wsList = new ArrayList();
      for (targetWsDetails = syncView.getWorkspaces().iterator(); targetWsDetails.hasNext();) { wsSync = (WorkspaceSyncDTO)targetWsDetails.next();
        
        if (wsSync.getWorkspaceItemId().equals(workspaceItemId)) {
          wsName = wsSync.getWorkspaceName();
          

          for (baselines = wsSync.getComponents().iterator(); baselines.hasNext(); 
              
              sourceComp.hasNext())
          {
            ComponentSyncDTO compSync = (ComponentSyncDTO)baselines.next();
            int blCount = 0;
            sourceComp = compSync.getIncomingBaselines().iterator(); continue;BaselineSyncDTO blSync = (BaselineSyncDTO)sourceComp.next();
            String matchedSelector = matchesSelector(config, blSync, selectors);
            
            if (matchedSelector != null) {
              blCount++;
              if (blCount > 1) {
                throw StatusHelper.inappropriateArgument(Messages.DeliverCmd_SPECIFY_ONLY_ONE_BASLELINE_PER_COMPONENT);
              }
              
              selectorSet.remove(matchedSelector);
              
              ParmsWorkspace ws = new ParmsWorkspace(compSync.getTargetIncomingRepositoryUrl(), compSync.getTargetIncomingWorkspaceItemId());
              ParmsWorkspace wsMatched = findWorkspace(wsList, ws);
              
              Object baselines = null;
              if (wsMatched != null) {
                baselines = (HashSet)ws2Baseline.get(wsMatched);
              }
              
              if (baselines == null) {
                baselines = new HashSet();
                
                ws2Baseline.put(ws, baselines);
                wsList.add(ws);
              }
              
              ((HashSet)baselines).add(blSync.getBaselineItemId());
            }
          }
          

          break;
        }
      }
    }
    

    if (selectorSet.size() > 0) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      err.println(Messages.AcceptCmd2_MISSING_BASELINE_SELECTOR_START);
      err = err.indent();
      for (String blSelector : selectorSet) {
        err.println(blSelector);
      }
      throw StatusHelper.ambiguousSelector(NLS.bind(Messages.AcceptCmd2_MISSING_BASELINE_SELECTOR_MESSAGE, wsName));
    }
    

    ParmsWorkspaceAcceptDetailed[] parms = new ParmsWorkspaceAcceptDetailed[ws2Baseline.size()];
    
    int i = 0;
    for (Map.Entry<ParmsWorkspace, HashSet<String>> entry : ws2Baseline.entrySet()) {
      ParmsWorkspaceAcceptDetailed detailed = new ParmsWorkspaceAcceptDetailed();
      
      workspace = targetWs;
      sourceWorkspace = ((ParmsWorkspace)entry.getKey());
      baselineItemIds = ((String[])((HashSet)entry.getValue()).toArray(new String[((HashSet)entry.getValue()).size()]));
      
      parms[(i++)] = detailed;
    }
    
    return parms;
  }
  
  private static String matchesSelector(IScmClientConfiguration config, String componentItemId, String componentName, HashSet<String> selectors, String repoUrl) throws FileSystemException
  {
    String compAlias = null;
    IUuidAliasRegistry.IUuidAlias alias = RepoUtil.lookupUuidAndAlias(componentItemId, repoUrl);
    if (alias != null) {
      compAlias = alias.getMonicker();
    }
    
    for (String selector : selectors) {
      if ((componentItemId.equals(selector)) || 
        (selector.equals(compAlias)) || 
        (selector.equals(componentName))) {
        return selector;
      }
    }
    
    return null;
  }
  
  private static String matchesSelector(IScmClientConfiguration config, BaselineSyncDTO blSync, List<String> selectors) {
    String baselineNumber = Integer.toString(blSync.getId());
    
    for (String selector : selectors) {
      IUuidAliasRegistry.IUuidAlias alias;
      try {
        alias = config.getAliasRegistry().findAliasByName(selector);
      } catch (IUuidAliasRegistry.NoSuchAliasException localNoSuchAliasException) { IUuidAliasRegistry.IUuidAlias alias;
        alias = null;
      }
      
      if ((blSync.getBaselineItemId().equals(selector)) || 
        (blSync.getBaselineName().equals(selector)) || 
        (baselineNumber.equals(selector)) || (
        (alias != null) && 
        (alias.getUuid().getUuidValue().equals(blSync.getBaselineItemId())))) {
        return selector;
      }
    }
    
    return null;
  }
}
