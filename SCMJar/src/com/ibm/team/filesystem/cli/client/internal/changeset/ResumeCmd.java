package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.lock.LockListCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptCmdOptions;
import com.ibm.team.filesystem.cli.client.internal.subcommands.AcceptResultDisplayer;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ConflictsCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.ResolveCmd;
import com.ibm.team.filesystem.cli.client.internal.subcommands.StatusCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsAcceptChangeSets;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsChangeSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsStructuredResultOptions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdate;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdateDilemmaHandler;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.StructuredUpdateReportDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.WorkspaceUpdateResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLineArgument;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.subcommands.HelpCmd;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.ActiveChangeSetsException;
import com.ibm.team.scm.common.ActiveChangeSetsOverlapException;
import com.ibm.team.scm.common.GapException;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.NWayConflictUnsupportedException;
import com.ibm.team.scm.common.PatchInProgressException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.osgi.util.NLS;


















public class ResumeCmd
  extends AbstractSubcommand
{
  public ResumeCmd() {}
  
  public void run()
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    ICommandLine subcmd = config.getSubcommandCommandLine();
    config.setEnableJSON(subcmd.hasOption(CommonOptions.OPT_JSON));
    
    boolean automergeDisabled = subcmd.hasOption(AcceptCmdOptions.OPT_DISABLE_AUTOMERGE);
    if ((automergeDisabled) && (subcmd.hasOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AcceptCmd2_INVALID_INPLACE_CONFLICT_MARKER_REQUEST, 
        AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER.getName(), 
        AcceptCmdOptions.OPT_DISABLE_AUTOMERGE.getName()));
    }
    
    IScmCommandLineArgument targetSelector = ScmCommandLineArgument.create(subcmd.getOptionValue(CommonOptions.OPT_STREAM_TARGET_SELECTOR, null), config);
    SubcommandUtil.validateArgument(targetSelector, RepoUtil.ItemType.WORKSPACE);
    
    ITeamRepository repo = null;
    
    ParmsWorkspace targetWs = new ParmsWorkspace();
    
    if (targetSelector == null) {
      List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
      if (wsInSandboxList.size() != 1) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.ResumeCmd_7, subcmd.getDefinition().getOption(CommonOptions.OPT_STREAM_TARGET_SELECTOR).getName()));
      }
      
      ISandboxWorkspace wsInSandbox = (ISandboxWorkspace)wsInSandboxList.iterator().next();
      repositoryUrl = RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
        Collections.singletonList(wsInSandbox));
      workspaceItemId = wsInSandbox.getWorkspaceItemId();
      
      repo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    }
    else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, targetSelector);
      
      IWorkspace ws = RepoUtil.getWorkspace(targetSelector.getItemSelector(), true, false, repo, config);
      
      repositoryUrl = repo.getRepositoryURI();
      workspaceItemId = ws.getItemId().getUuidValue();
    }
    

    ParmsWorkspaceUpdate parms = new ParmsWorkspaceUpdate();
    generateResumeParms(config, client, subcmd, parms, targetWs, repo);
    
    if (config.isDryRun()) {
      return;
    }
    

    try
    {
      result = client.postWorkspaceUpdate(parms, null);
    } catch (TeamRepositoryException e) {
      WorkspaceUpdateResultDTO result;
      PatchInProgressException port = (PatchInProgressException)SubcommandUtil.findExceptionByType(PatchInProgressException.class, e);
      if (port != null) {
        throw StatusHelper.portsInProgress(port.getLocalizedMessage());
      }
      
      if (isActiveChangeSetsException(e)) {
        throw StatusHelper.disallowed(Messages.ResumeCmd_0);
      }
      
      PermissionDeniedException pde = (PermissionDeniedException)SubcommandUtil.findExceptionByType(PermissionDeniedException.class, e);
      if (pde != null) {
        throw StatusHelper.permissionFailure(pde, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      GapException gap = (GapException)SubcommandUtil.findExceptionByType(GapException.class, e);
      if (gap != null) {
        String commandName = SubcommandUtil.getExecutionString(config.getSubcommandDefinition()).toString();
        ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, HelpCmd.class);
        throw StatusHelper.gap(Messages.AcceptCmd_MISSING_CHANGE_SETS + "\n" + NLS.bind(Messages.ResumeCmd_HINT_ON_GAP, new String[] {
          config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString(), commandName }));
      }
      
      NWayConflictUnsupportedException nway = (NWayConflictUnsupportedException)SubcommandUtil.findExceptionByType(NWayConflictUnsupportedException.class, e);
      if (nway != null) {
        throw StatusHelper.nWayConflict(Messages.ResumeCmd_2);
      }
      
      ActiveChangeSetsOverlapException overlap = (ActiveChangeSetsOverlapException)SubcommandUtil.findExceptionByType(ActiveChangeSetsOverlapException.class, e);
      if (overlap != null) {
        throw StatusHelper.disallowed(Messages.ResumeCmd_11);
      }
      throw StatusHelper.wrap(Messages.ResumeCmd_3, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    WorkspaceUpdateResultDTO result;
    if (result.isCancelled()) {
      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.ResumeCmd_UNCOMMITTED_COMPLAINT, Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
    }
    
    IndentingPrintStream ps = new IndentingPrintStream(config.getContext().stdout());
    
    if (!config.isJSONEnabled()) {
      ps.println(Messages.ResumeCmd_CS_RESUME);
    }
    

    List<ShareableDTO> deletedContentShareables = new ArrayList();
    List<String> unresolvedWorkspaces = AcceptCmd.autoresolve(client, result, automergeDisabled, 
      deletedContentShareables, config);
    
    boolean verbose = subcmd.hasOption(CommonOptions.OPT_VERBOSE);
    AcceptResultDisplayer.showResult(client, repo, false, parms, result, deletedContentShareables, 
      verbose, config);
    
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    if ((subcmd.hasOption(CommonOptions.OPT_ACQUIRE_LOCK)) && (result.getLocksWereHeld() != null) && (result.getLocksWereHeld().size() > 0)) {
      ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, LockListCmd.class);
      out.println(NLS.bind(Messages.ResumeCmd_ITEM_LOCKED_IN_STREAM, config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp).toString()));
    }
    
    if ((unresolvedWorkspaces.size() > 0) && 
      (subcmd.hasOption(AcceptCmdOptions.OPT_INPLACE_CONFLICT_HANDLER))) {
      AcceptCmd.markInPlaceConflicts(config, result, client, verbose);
      out.println(Messages.Conflicts_InPlaceMarkers_Help);
    }
    
    ISubcommandDefinition defnTemp3;
    if (hasConflicts(result)) {
      ISubcommandDefinition defnTemp1 = SubcommandUtil.getClassSubCommandDefn(config, ResolveCmd.class);
      ISubcommandDefinition defnTemp2 = SubcommandUtil.getClassSubCommandDefn(config, ConflictsCmd.class);
      defnTemp3 = SubcommandUtil.getClassSubCommandDefn(config, StatusCmd.class);
      
      throw StatusHelper.conflict(Messages.ResumeCmd_4 + " " + NLS.bind(
        Messages.AcceptCmd_CONFLICT_GUIDANCE, new String[] {
        config.getContext().getAppName(), SubcommandUtil.getExecutionString(defnTemp1).toString(), SubcommandUtil.getExecutionString(defnTemp2).toString(), 
        SubcommandUtil.getExecutionString(defnTemp3).toString() }));
    }
    

    if (result.isSetEclipseReadFailureMessage()) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      for (Object nextMsg : result.getEclipseReadFailureMessage()) {
        err.println((String)nextMsg);
      }
    }
  }
  
  private boolean hasConflicts(WorkspaceUpdateResultDTO result)
  {
    for (StructuredUpdateReportDTO wsReport : result.getStructuredResult()) {
      if (wsReport.isHasConflicts()) {
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
  
  private void generateResumeParms(IScmClientConfiguration config, IFilesystemRestClient client, ICommandLine subcmd, ParmsWorkspaceUpdate parms, ParmsWorkspace targetWs, ITeamRepository repo)
    throws FileSystemException
  {
    List<IScmCommandLineArgument> selectors = new ArrayList(subcmd.getOptionValues(CommonOptions.OPT_CHANGESET_SELECTORS).size());
    for (ICommandLineArgument selector : subcmd.getOptionValues(CommonOptions.OPT_CHANGESET_SELECTORS)) {
      selectors.add(ScmCommandLineArgument.create(selector, config));
    }
    SubcommandUtil.validateArgument(selectors, RepoUtil.ItemType.CHANGESET);
    RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, selectors, repo, config);
    

    RepoUtil.findChangeSets(RepoUtil.getSelectors(selectors), false, workspaceItemId, 
      "workspace", repositoryUrl, client, config);
    

    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    

    structuredResultOptions = new ParmsStructuredResultOptions();
    workspaceUpdateDilemmaHandler = new ParmsWorkspaceUpdateDilemmaHandler();
    workspaceUpdateDilemmaHandler.disconnectedComponentsDirection = "continue";
    
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
    

    String gapStrategy = "fail";
    if (subcmd.hasOption(AcceptCmdOptions.OPT_ACCEPT_WITH_PORTING))
      gapStrategy = "continue";
    workspaceUpdateDilemmaHandler.gapDirection = gapStrategy;
    
    if (subcmd.hasOption(AcceptCmdOptions.OPT_ACCEPT_WITH_PORTING)) {
      workspaceUpdateDilemmaHandler.portInProgressDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.portInProgressDirection = "fail";
    }
    







    ParmsAcceptChangeSets update = new ParmsAcceptChangeSets();
    changeSets = normalizeCsUuidAndAlias(repo, selectors, config);
    workspace = targetWs;
    
    acceptChangeSets = new ParmsAcceptChangeSets[] { update };
    


    if (config.getSubcommandCommandLine().hasOption(ResumeCmdOptions.OPT_RESUME_MISSING_CHANGESETS))
    {

      List<ParmsChangeSet> changes = AcceptCmd.fetchGapFillingChangeSets(repo, targetWs, selectors);
      

      if ((changes != null) && (!changes.isEmpty())) {
        ParmsAcceptChangeSets newUpdate = new ParmsAcceptChangeSets();
        workspace = targetWs;
        changeSets = ((ParmsChangeSet[])changes.toArray(new ParmsChangeSet[changes.size()]));
        ParmsAcceptChangeSets[] b = new ParmsAcceptChangeSets[acceptChangeSets.length + 1];
        System.arraycopy(acceptChangeSets, 0, b, 0, acceptChangeSets.length);
        b[acceptChangeSets.length] = newUpdate;
        acceptChangeSets = b;
      }
    }
    
    if (!config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_ACQUIRE_LOCK)) {
      autoAcquireLocks = Boolean.valueOf(((ScmClientConfiguration)config).getPersistentPreferences().getAcquireLockForResume());
    }
    else {
      autoAcquireLocks = Boolean.valueOf(Boolean.parseBoolean(config.getSubcommandCommandLine().getOption(CommonOptions.OPT_ACQUIRE_LOCK).toString()));
    }
  }
  
  private static ParmsChangeSet[] normalizeCsUuidAndAlias(ITeamRepository repo, List<IScmCommandLineArgument> csSelectors, IScmClientConfiguration config) throws FileSystemException {
    ParmsChangeSet[] normalized = new ParmsChangeSet[csSelectors.size()];
    
    for (int i = 0; i < normalized.length; i++) {
      IScmCommandLineArgument selector = (IScmCommandLineArgument)csSelectors.get(i);
      
      UUID uuid = RepoUtil.lookupUuid(selector.getItemSelector());
      
      if (uuid != null) {
        ParmsChangeSet parmsCs = new ParmsChangeSet();
        changeSetItemId = uuid.getUuidValue();
        repositoryUrl = repo.getRepositoryURI();
        normalized[i] = parmsCs;
      }
      else
      {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.ResumeCmd_6, selector.getItemSelector()));
      }
    }
    return normalized;
  }
}
