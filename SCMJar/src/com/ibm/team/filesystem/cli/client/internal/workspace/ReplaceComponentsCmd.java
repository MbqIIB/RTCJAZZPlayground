package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaseline;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsOutOfSyncInstructions;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceComponentReplaceWithBaseline;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceReplaceWithSnapshot;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceReplaceWithWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdate;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdateDilemmaHandler;
import com.ibm.team.filesystem.common.internal.rest.client.changeset.WorkspaceUpdateResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;
















public class ReplaceComponentsCmd
  extends AbstractSubcommand
{
  public ReplaceComponentsCmd() {}
  
  static enum ReplaceType
  {
    WORKSPACE("workspace", new String[] { "workspace", "stream", "ws", "s" }), 
    SNAPSHOT("baselineset", new String[] { "snapshot", "ss" });
    
    static final String[] NAMES = { WORKSPACEtypeStrings[0], WORKSPACEtypeStrings[1], SNAPSHOTtypeStrings[0] };
    final String[] typeStrings;
    final String wireName;
    
    private ReplaceType(String wireName, String... t)
    {
      this.wireName = wireName;
      typeStrings = t;
    }
    
    String[] getNames() {
      return typeStrings;
    }
    
    String getWireName() {
      return wireName;
    }
  }
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    ParmsWorkspaceUpdate parms = new ParmsWorkspaceUpdate();
    generateReplaceComponentParms(cli, client, parms, config);
    
    if (config.isDryRun()) {
      return;
    }
    

    try
    {
      result = client.postWorkspaceUpdate(parms, null);
    } catch (TeamRepositoryException e) { WorkspaceUpdateResultDTO result;
      PermissionDeniedException pde = (PermissionDeniedException)SubcommandUtil.findExceptionByType(PermissionDeniedException.class, e);
      if (pde != null) {
        throw StatusHelper.permissionFailure(pde, new IndentingPrintStream(config.getContext().stderr()));
      }
      
      throw StatusHelper.wrap(Messages.ReplaceComponentsCmd_COULD_NOT_UPDATE, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    
    WorkspaceUpdateResultDTO result;
    if (result.isCancelled())
    {

      int noOfUncheckedInChanges = SubcommandUtil.getNoOfUncheckedInChanges(result.getConfigurationsWithUncheckedInChanges());
      if (noOfUncheckedInChanges > 0) {
        throw StatusHelper.uncheckedInChanges(NLS.bind(Messages.CreateBaselineCmd_ERROR_UNCOMMITTED_CHANGES_TO_LOCAL_FILESYSTEM, Integer.valueOf(noOfUncheckedInChanges), CommonOptions.OPT_OVERWRITE_UNCOMMITTED.getName()));
      }
      if (result.getNoBackupBaselinesComponents().size() > 0) {
        throw StatusHelper.noBackupBaseline(NLS.bind(Messages.ReplaceComponentsCmd_NO_BACKUP_BASELINE, ReplaceComponentsCmdOptions.OPT_NO_BACKUP.getName()));
      }
      if ((result.getSubcomponentsInMultipleHierarchies().size() > 0) || 
        (result.getSelectedComponentsInMultipleHierarchies().size() > 0)) {
        throw StatusHelper.componentsInMultipleHierarchies(NLS.bind(Messages.ReplaceComponentsCmd_MULTI_HIERARCHY, ReplaceComponentsCmdOptions.OPT_ALLOW_MULTIPLE_HIERARCHY.getName()));
      }
      if (result.getInaccessibleComponentsNotInSeed().size() > 0) {
        throw StatusHelper.inaccessibleComponentsNotInSeed(NLS.bind(Messages.ReplaceComponentsCmd_INACCESSIBLE_COMPONENTS_NOT_IN_SEED, ReplaceComponentsCmdOptions.OPT_ALLOW_MULTIPLE_HIERARCHY.getName()));
      }
    }
    
    showResult(result, config);
  }
  
  private void showResult(WorkspaceUpdateResultDTO result, IScmClientConfiguration config) {
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    if (result.getSandboxUpdateDilemma().getBackedUpToShed().size() > 0) {
      SubcommandUtil.showShedUpdate(Messages.AcceptResultDisplayer_SHED_MESSAGE, out, result.getSandboxUpdateDilemma().getBackedUpToShed());
    }
    
    if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
      SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
    }
    
    if (result.isSetEclipseReadFailureMessage()) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      for (Object nextMsg : result.getEclipseReadFailureMessage()) {
        err.println((String)nextMsg);
      }
    }
  }
  
  private void generateReplaceComponentParms(ICommandLine cli, IFilesystemRestClient client, ParmsWorkspaceUpdate parms, IScmClientConfiguration config) throws FileSystemException
  {
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    

    workspaceUpdateDilemmaHandler = new ParmsWorkspaceUpdateDilemmaHandler();
    workspaceUpdateDilemmaHandler.disconnectedComponentsDirection = "continue";
    workspaceUpdateDilemmaHandler.componentFlowDirection = "no";
    workspaceUpdateDilemmaHandler.componentReplacementCandidatesDirection = "continue";
    workspaceUpdateDilemmaHandler.activeChangeSetsDirection = "no";
    workspaceUpdateDilemmaHandler.activeChangeSetsOverlapDirection = "no";
    if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_NO_BACKUP)) {
      workspaceUpdateDilemmaHandler.noBackupBaselineDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.noBackupBaselineDirection = "cancel";
    }
    if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_ALLOW_MULTIPLE_HIERARCHY)) {
      workspaceUpdateDilemmaHandler.componentInMultipleHierarchiesDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.componentInMultipleHierarchiesDirection = "cancel";
    }
    if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_SKIP_INACCESSIBLE_COMPONENTS)) {
      workspaceUpdateDilemmaHandler.inaccessibleComponentsNotInSeedDirection = "continue";
    } else {
      workspaceUpdateDilemmaHandler.inaccessibleComponentsNotInSeedDirection = "cancel";
    }
    
    outOfSyncInstructions = new ParmsOutOfSyncInstructions();
    outOfSyncInstructions.outOfSyncNoPendingChanges = "load";
    outOfSyncInstructions.outOfSyncWithPendingChanges = "load";
    outOfSyncInstructions.deleteRemovedShares = Boolean.valueOf(true);
    
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if (cli.hasOption(CommonOptions.OPT_OVERWRITE_UNCOMMITTED)) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    

    ReplaceType replaceType = findType(cli.getOption(ReplaceComponentsCmdOptions.OPT_REPLACE_TYPE));
    

    if ((replaceType == ReplaceType.SNAPSHOT) && (cli.hasOption(ReplaceComponentsCmdOptions.OPT_BASELINE))) {
      throw StatusHelper.argSyntax(Messages.ReplaceComponentsCmd_SNAPSHOT_INVALID_OPTION);
    }
    
    if ((cli.hasOption(CommonOptions.OPT_ALL)) && (cli.hasOption(ReplaceComponentsCmdOptions.OPT_COMPONENTS_SELECTOR))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ReplaceComponentsCmd_COMPONENT_SELECTOR_CANNOT_SPECIFY_BOTH, CommonOptions.OPT_ALL.getName()));
    }
    
    if ((!cli.hasOption(CommonOptions.OPT_ALL)) && (!cli.hasOption(ReplaceComponentsCmdOptions.OPT_COMPONENTS_SELECTOR))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ReplaceComponentsCmd_COMPONENT_SELECTOR_SPECIFY_ONE, CommonOptions.OPT_ALL.getName()));
    }
    

    IScmCommandLineArgument targetWsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(targetWsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository targetRepo = RepoUtil.loginUrlArgAncestor(config, client, targetWsSelector);
    IWorkspace targetWs = RepoUtil.getWorkspace(targetWsSelector.getItemSelector(), true, true, targetRepo, config);
    

    if (replaceType == ReplaceType.WORKSPACE) {
      if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_BASELINE)) {
        workspaceReplaceWithBaseline = generateBaselineParms(targetWs, targetRepo, config, client, cli);
      } else {
        workspaceReplaceWithWorkspace = generateWorkspaceParms(targetWs, targetRepo, config, client, cli);
      }
      if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_HIERARCHY)) {
        hierarchicalOperation = true;
        workspaceUpdateDilemmaHandler.componentFlowDirection = "continue";
      }
    } else {
      workspaceReplaceWithSnapshot = generateSnapshotParms(targetWs, targetRepo, config, client, cli);
    }
  }
  
  private ParmsWorkspaceReplaceWithWorkspace[] generateWorkspaceParms(IWorkspace targetWs, ITeamRepository targetRepo, IScmClientConfiguration config, IFilesystemRestClient client, ICommandLine cli) throws FileSystemException
  {
    IScmCommandLineArgument selector = ScmCommandLineArgument.create(cli.getOptionValue(ReplaceComponentsCmdOptions.OPT_REPLACE_ITEM), config);
    SubcommandUtil.validateArgument(selector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    

    ITeamRepository srcRepo = RepoUtil.loginUrlArgAncestor(config, client, selector);
    IWorkspace ws = RepoUtil.getWorkspace(selector.getItemSelector(), true, true, srcRepo, config);
    

    Map<String, String> componentIdsToName = getComponentsToBeReplaced(targetWs, srcRepo, targetRepo, cli, config, client);
    

    ParmsWorkspace sourceWs = new ParmsWorkspace(srcRepo.getRepositoryURI(), ws.getItemId().getUuidValue());
    WorkspaceDetailsDTO sourceWsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(sourceWs), client, config).get(0);
    List<WorkspaceComponentDTO> sourceWsComps = sourceWsDetails.getComponents();
    

    List<String> componentsSkipped = new ArrayList(componentIdsToName.values());
    List<String> componentsToBeReplaced = new ArrayList();
    for (WorkspaceComponentDTO comp : sourceWsComps) {
      if (componentIdsToName.keySet().contains(comp.getItemId())) {
        componentsToBeReplaced.add(comp.getItemId());
        componentsSkipped.remove(componentIdsToName.get(comp.getItemId()));
      }
    }
    

    if (componentsSkipped.size() > 0) {
      IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
      
      out.println(NLS.bind(Messages.ReplaceComponentsCmd_COMPONENT_NOT_IN_WORKSPACE, 
        AliasUtil.selector(sourceWsDetails.getName(), UUID.valueOf(sourceWsDetails.getItemId()), 
        sourceWsDetails.getRepositoryURL(), 
        sourceWsDetails.isStream() ? RepoUtil.ItemType.STREAM : RepoUtil.ItemType.WORKSPACE)));
      out.indent();
      for (String comp : componentsSkipped) {
        out.println(comp);
      }
    }
    

    ParmsWorkspaceReplaceWithWorkspace[] parms = new ParmsWorkspaceReplaceWithWorkspace[1];
    
    parms[0] = new ParmsWorkspaceReplaceWithWorkspace();
    0sourceWorkspace = new ParmsWorkspace(srcRepo.getRepositoryURI(), workspaceItemId);
    0workspace = new ParmsWorkspace(targetRepo.getRepositoryURI(), targetWs.getItemId().getUuidValue());
    0componentItemIds = ((String[])componentsToBeReplaced.toArray(new String[componentsToBeReplaced.size()]));
    
    return parms;
  }
  
  private ParmsWorkspaceComponentReplaceWithBaseline[] generateBaselineParms(IWorkspace targetWs, ITeamRepository targetRepo, IScmClientConfiguration config, IFilesystemRestClient client, ICommandLine cli)
    throws FileSystemException
  {
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ReplaceComponentsCmdOptions.OPT_REPLACE_ITEM), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    IScmCommandLineArgument blSelector = ScmCommandLineArgument.create(cli.getOptionValue(ReplaceComponentsCmdOptions.OPT_BASELINE), config);
    SubcommandUtil.validateArgument(blSelector, RepoUtil.ItemType.BASELINE);
    IScmCommandLineArgument sourceSelector = ScmCommandLineArgument.create(cli.getOptionValue(ReplaceComponentsCmdOptions.OPT_SOURCE), config);
    SubcommandUtil.validateArgument(sourceSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    

    ITeamRepository srcRepo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    IWorkspace ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, srcRepo, config);
    

    Map<String, String> componentIdsToName = getComponentsToBeReplaced(targetWs, srcRepo, targetRepo, cli, config, client);
    


    ParmsWorkspace sourceWs = new ParmsWorkspace(srcRepo.getRepositoryURI(), ws.getItemId().getUuidValue());
    WorkspaceDetailsDTO sourceWsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(sourceWs), client, config).get(0);
    List<WorkspaceComponentDTO> sourceWsComps = sourceWsDetails.getComponents();
    

    List<IBaseline> baselines = new ArrayList();
    IBaseline baseline; for (WorkspaceComponentDTO sourceComp : sourceWsComps) {
      if (componentIdsToName.keySet().contains(sourceComp.getItemId())) {
        baseline = RepoUtil.getBaseline(blSelector.getItemSelector(), sourceComp.getItemId(), 
          sourceComp.getName(), srcRepo, client, config);
        baselines.add(baseline);
      }
    }
    

    List<String> baselineIds = new ArrayList();
    for (IBaseline baseline : baselines) {
      IComponentHandle compHandle = baseline.getComponent();
      
      if (componentIdsToName.keySet().contains(compHandle.getItemId().getUuidValue())) {
        baselineIds.add(baseline.getItemId().getUuidValue());
      }
    }
    
    if (baselineIds.size() == 0) {
      config.getContext().stdout().println(NLS.bind(Messages.ReplaceComponentsCmd_BASELINE_NOT_IN_COMPONENT, blSelector.getItemSelector()));
    }
    

    ParmsWorkspaceComponentReplaceWithBaseline[] parms = new ParmsWorkspaceComponentReplaceWithBaseline[baselineIds.size()];
    
    IWorkspaceHandle operationSource = null;
    ITeamRepository sourceRepo = null;
    if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_SOURCE)) {
      sourceRepo = RepoUtil.loginUrlArgAncestor(config, client, sourceSelector);
      operationSource = RepoUtil.getWorkspace(sourceSelector.getItemSelector(), true, true, targetRepo, config);
    }
    
    int count = 0;
    for (String baselineId : baselineIds) {
      ParmsWorkspaceComponentReplaceWithBaseline replaceWithBaseline = new ParmsWorkspaceComponentReplaceWithBaseline();
      workspace = new ParmsWorkspace(targetRepo.getRepositoryURI(), targetWs.getItemId().getUuidValue());
      baseline = new ParmsBaseline();
      baseline.baselineItemId = baselineId;
      baseline.repositoryUrl = srcRepo.getRepositoryURI();
      if ((operationSource != null) && (sourceRepo != null)) {
        sourceWorkspace = new ParmsWorkspace(sourceRepo.getRepositoryURI(), operationSource.getItemId().getUuidValue());
      }
      
      parms[(count++)] = replaceWithBaseline;
    }
    
    return parms;
  }
  

  private ParmsWorkspaceReplaceWithSnapshot[] generateSnapshotParms(IWorkspace targetWs, ITeamRepository targetRepo, IScmClientConfiguration config, IFilesystemRestClient client, ICommandLine cli)
    throws FileSystemException
  {
    IScmCommandLineArgument ssSelector = ScmCommandLineArgument.create(cli.getOptionValue(ReplaceComponentsCmdOptions.OPT_REPLACE_ITEM), config);
    SubcommandUtil.validateArgument(ssSelector, RepoUtil.ItemType.SNAPSHOT);
    IScmCommandLineArgument sourceSelector = ScmCommandLineArgument.create(cli.getOptionValue(ReplaceComponentsCmdOptions.OPT_SOURCE), config);
    SubcommandUtil.validateArgument(sourceSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    
    ITeamRepository srcRepo = null;
    String selector = ssSelector.getItemSelector();
    if ((ssSelector.getRepositorySelector() != null) && (ssSelector.isRepoExplicit())) {
      srcRepo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    } else {
      srcRepo = targetRepo;
    }
    

    Map<String, String> componentIdsToName = getComponentsToBeReplaced(targetWs, srcRepo, targetRepo, cli, config, client);
    
    IBaselineSet snapshot = RepoUtil.getSnapshot(null, selector, srcRepo, config);
    

    List<String> componentsToBeReplaced = new ArrayList();
    List<String> componentsSkipped = new ArrayList(componentIdsToName.values());
    List<IBaseline> baselines = RepoUtil.getExistingAccessibleItems(
      IBaseline.ITEM_TYPE, snapshot.getBaselines(), srcRepo, config);
    String compId; for (IBaseline baseline : baselines) {
      compId = baseline.getComponent().getItemId().getUuidValue();
      
      if (componentIdsToName.keySet().contains(compId)) {
        componentsToBeReplaced.add(compId);
        componentsSkipped.remove(componentIdsToName.get(compId));
      }
    }
    

    if (componentsSkipped.size() > 0) {
      IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
      
      out.println(NLS.bind(Messages.ReplaceComponentsCmd_COMPONENT_NOT_IN_WORKSPACE, 
        AliasUtil.selector(snapshot.getName(), snapshot.getItemId(), 
        srcRepo.getRepositoryURI(), RepoUtil.ItemType.SNAPSHOT)));
      out.indent();
      for (String comp : componentsSkipped) {
        out.println(comp);
      }
    }
    
    IWorkspaceHandle operationSource = null;
    ITeamRepository sourceRepo = null;
    if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_SOURCE)) {
      sourceRepo = RepoUtil.loginUrlArgAncestor(config, client, sourceSelector);
      operationSource = RepoUtil.getWorkspace(sourceSelector.getItemSelector(), true, true, targetRepo, config);
    }
    

    ParmsWorkspaceReplaceWithSnapshot[] parms = new ParmsWorkspaceReplaceWithSnapshot[1];
    
    parms[0] = new ParmsWorkspaceReplaceWithSnapshot();
    0workspace = new ParmsWorkspace(targetRepo.getRepositoryURI(), targetWs.getItemId().getUuidValue());
    0componentItemIds = ((String[])componentsToBeReplaced.toArray(new String[componentsToBeReplaced.size()]));
    0baselineSet = new ParmsBaselineSet();
    0baselineSet.baselineSetItemId = snapshot.getItemId().getUuidValue();
    0baselineSet.repositoryUrl = srcRepo.getRepositoryURI();
    
    if ((operationSource != null) && (sourceRepo != null)) {
      0sourceWorkspace = new ParmsWorkspace(sourceRepo.getRepositoryURI(), operationSource.getItemId().getUuidValue());
    }
    
    return parms;
  }
  
  private Map<String, String> getComponentsToBeReplaced(IWorkspace ws, ITeamRepository srcRepo, ITeamRepository targetRepo, ICommandLine cli, IScmClientConfiguration config, IFilesystemRestClient client)
    throws FileSystemException
  {
    ParmsWorkspace targetWs = new ParmsWorkspace(targetRepo.getRepositoryURI(), ws.getItemId().getUuidValue());
    WorkspaceDetailsDTO targetWsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(targetWs), client, config).get(0);
    List<WorkspaceComponentDTO> targetWsComps = targetWsDetails.getComponents();
    
    Map<String, String> componentIdsToName = new HashMap();
    boolean found;
    if (cli.hasOption(ReplaceComponentsCmdOptions.OPT_COMPONENTS_SELECTOR)) {
      List<String> componentSelectors = cli.getOptions(ReplaceComponentsCmdOptions.OPT_COMPONENTS_SELECTOR);
      found = false;
      
      for (String selector : componentSelectors) {
        IUuidAliasRegistry.IUuidAlias uuid = RepoUtil.lookupUuidAndAlias(selector);
        
        int matched = 0;
        for (WorkspaceComponentDTO comp : targetWsComps) {
          if (((uuid != null) && (RepoUtil.uuidAndRepoMatches(UUID.valueOf(comp.getItemId()), targetRepo, uuid))) || 
            (selector.equals(comp.getName()))) {
            componentIdsToName.put(comp.getItemId(), comp.getName());
            matched++;
            found = true;
          }
        }
        
        if (matched == 0) {
          new IndentingPrintStream(config.getContext().stdout()).println(NLS.bind(Messages.ReplaceComponentsCmd_SKIPPING_COMPONENT, selector));
        } else if (matched > 1) {
          throw StatusHelper.ambiguousSelector(NLS.bind(Messages.ReplaceComponentsCmd_AMBIGUOUS_COMPONENT, selector));
        }
      }
      
      if (!found) {
        throw StatusHelper.failure(Messages.ReplaceComponentsCmd_NOTHING_TO_REPLACE, null);
      }
    }
    else if (cli.hasOption(CommonOptions.OPT_ALL))
    {
      for (WorkspaceComponentDTO comp : targetWsComps) {
        componentIdsToName.put(comp.getItemId(), comp.getName());
      }
    }
    
    return componentIdsToName;
  }
  
  private ReplaceType findType(String typeString) throws FileSystemException {
    for (ReplaceType t : ) {
      for (String candidate : t.getNames()) {
        if (candidate.equals(typeString)) {
          return t;
        }
      }
    }
    
    throw StatusHelper.argSyntax(NLS.bind(Messages.ReplaceComponentsCmd_UNKNOWN_TYPE_STRING, typeString, StringUtil.join(", ", ReplaceType.NAMES)));
  }
}
