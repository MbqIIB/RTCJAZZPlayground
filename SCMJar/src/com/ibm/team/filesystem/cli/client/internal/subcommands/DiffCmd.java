package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.client.internal.listcommand.ListChangesCmd;
import com.ibm.team.filesystem.cli.client.internal.listcommand.ListChangesetsCmd;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.ITypedPreferenceRegistry;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.IShareable;
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.internal.ClientConfigurationChangeFactory;
import com.ibm.team.filesystem.client.internal.ClientFileStateFactory;
import com.ibm.team.filesystem.client.internal.RelativeLocation;
import com.ibm.team.filesystem.client.internal.SharingManager;
import com.ibm.team.filesystem.client.internal.patches.CreatePatchDilemmaHandler;
import com.ibm.team.filesystem.client.internal.patches.CreatePatchUtil;
import com.ibm.team.filesystem.client.internal.snapshot.SnapshotId;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaseline;
import com.ibm.team.filesystem.client.rest.parameters.ParmsContext;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.FileLineDelimiter;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.changemodel.ChangeDescription;
import com.ibm.team.filesystem.common.changemodel.ConfigurationChange;
import com.ibm.team.filesystem.common.changemodel.FileChange;
import com.ibm.team.filesystem.common.changemodel.FileState;
import com.ibm.team.filesystem.common.changemodel.IPathResolver;
import com.ibm.team.filesystem.common.changemodel.ResolvedConfigurationChangePaths;
import com.ibm.team.filesystem.common.changemodel.VersionablePath;
import com.ibm.team.filesystem.common.internal.patch.CreateDiffUtil;
import com.ibm.team.filesystem.common.internal.patch.HunkRange;
import com.ibm.team.filesystem.common.internal.patch.StringDiffParticipant;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ComponentSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.LocalChangeSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.SyncViewDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.WorkspaceSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.impl.ChangeFolderSyncDTOImpl;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.ConfigurationChangeFactory;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.CopyFileAreaPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.FallbackPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.RemotePathResolver;
import com.ibm.team.filesystem.rcp.core.internal.changes.model.SnapshotPathResolver;
import com.ibm.team.filesystem.rcp.core.internal.compare.AbstractOpenInExternalCompareOperation;
import com.ibm.team.filesystem.rcp.core.internal.compare.ExternalCompareToolsUtil;
import com.ibm.team.filesystem.rcp.core.internal.compare.OpenFileItemInExternalCompareOperation;
import com.ibm.team.filesystem.rcp.core.internal.compare.OpenShareableInExternalCompareOperation;
import com.ibm.team.filesystem.rcp.core.internal.patches.FileStateFactory;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.ItemNotFoundException;
import com.ibm.team.repository.common.LogFactory;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.client.IBaselineConnection;
import com.ibm.team.scm.client.IChangeHistory;
import com.ibm.team.scm.client.IConnection;
import com.ibm.team.scm.client.IHistoricBaselineIterator;
import com.ibm.team.scm.client.IVersionableManager;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.ComponentNotInWorkspaceException;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IBaselineHandle;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IChange;
import com.ibm.team.scm.common.IChangeHistoryEntryChange;
import com.ibm.team.scm.common.IChangeSet;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IComponentInfo;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.VersionablePermissionDeniedException;
import com.ibm.team.scm.common.VersionedContentDeleted;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.IScmRestService.ParmsChangeSetSearchCriteria;
import com.ibm.team.scm.common.internal.util.ItemId;
import com.ibm.team.scm.common.internal.util.SiloedItemId;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.logging.Log;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;












public class DiffCmd
  extends AbstractSubcommand
{
  IFilesystemRestClient client;
  boolean hasContentDiff;
  boolean displayContentDiff;
  boolean displayPropertyDiff;
  String componentItemSelector;
  int maxValue;
  IComponent component;
  Map<String, IBaseline> baselineMap = new HashMap();
  


  String compBaselineSelector = null;
  boolean hasMoreItems = false;
  
  private final Log logger = LogFactory.getLog(DiffCmd.class.getName());
  public DiffCmd() {}
  
  private static class StateSelector {
    public static final String TYPE_FILE = "file";
    public static final String TYPE_WORKSPACE = "workspace";
    public static final String TYPE_STREAM = "stream";
    public static final String TYPE_BASELINE = "baseline";
    public static final String TYPE_CHANGESET = "changeset";
    public static final String TYPE_SNAPSHOT = "snapshot";
    private final String type;
    private final IScmCommandLineArgument selector;
    
    public StateSelector(String type, IScmCommandLineArgument selector) {
      this.type = type;
      this.selector = selector;
    }
    
    public String getType() {
      return type;
    }
    
    public IScmCommandLineArgument getSelector() {
      return selector;
    }
    
    public String getItemSelector() {
      return selector.getItemSelector();
    }
  }
  
  private static class ContextDiffInput {
    ParmsContext context;
    IPathResolver pathResolver;
    boolean hasComponent;
    String type;
    
    ContextDiffInput(ParmsContext context, IPathResolver pathResolver, boolean hasComponent, String type) {
      this.context = context;
      this.pathResolver = pathResolver;
      this.hasComponent = hasComponent;
      this.type = type;
    }
  }
  
  private static enum PropertyModificationType {
    ADD_PROPERTY,  DELETE_PROPERTY,  MODIFY_PROPERTY;
  }
  
  public void run()
    throws FileSystemException
  {
    ResourcesPlugin.getWorkspace();
    

    StateSelector before = null;
    StateSelector after = null;
    List<String> changeSelectors = null;
    boolean fullPatch = false;
    IScmCommandLineArgument wsSelector = null;
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if ((!cli.hasOption(DiffCmdOpts.OPT_AFTER_TYPE)) || (!cli.hasOption(DiffCmdOpts.OPT_AFTER_SELECTOR))) {
      throw StatusHelper.argSyntax(Messages.DiffCmd_1);
    }
    after = 
      new StateSelector(
      cli.getOption(DiffCmdOpts.OPT_AFTER_TYPE), ScmCommandLineArgument.create(cli.getOptionValue(DiffCmdOpts.OPT_AFTER_SELECTOR), config));
    SubcommandUtil.validateArgument(after.getSelector(), new RepoUtil.ItemType[] { RepoUtil.ItemType.VERSIONABLE, 
      RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM, RepoUtil.ItemType.BASELINE, RepoUtil.ItemType.CHANGESET, RepoUtil.ItemType.SNAPSHOT });
    
    if (cli.hasOption(DiffCmdOpts.OPT_BEFORE_TYPE)) {
      if (!cli.hasOption(DiffCmdOpts.OPT_BEFORE_SELECTOR)) {
        throw StatusHelper.argSyntax(Messages.DiffCmd_0);
      }
      before = 
        new StateSelector(
        cli.getOption(DiffCmdOpts.OPT_BEFORE_TYPE), 
        ScmCommandLineArgument.create(cli.getOptionValue(DiffCmdOpts.OPT_BEFORE_SELECTOR), config));
      SubcommandUtil.validateArgument(before.getSelector(), new RepoUtil.ItemType[] {
        RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM, RepoUtil.ItemType.BASELINE, RepoUtil.ItemType.CHANGESET, RepoUtil.ItemType.SNAPSHOT });
    }
    
    fullPatch = cli.hasOption(DiffCmdOpts.OPT_FULL_PATCH);
    if ((fullPatch) && ((cli.hasOption(DiffCmdOpts.OPT_DISPLAY)) || (cli.hasOption(DiffCmdOpts.OPT_EXTERNAL_COMPARE)))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.DiffCmd_FULLPATCH_MUTUALLYEXCLUSIVE, 
        new String[] { DiffCmdOpts.OPT_DISPLAY.getName(), DiffCmdOpts.OPT_EXTERNAL_COMPARE.getName(), DiffCmdOpts.OPT_FULL_PATCH.getName() }));
    }
    if (cli.hasOption(DiffCmdOpts.OPT_WORKSPACE)) {
      wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(DiffCmdOpts.OPT_WORKSPACE), config);
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    }
    
    display = cli.getOption(DiffCmdOpts.OPT_DISPLAY, "c");
    displayContentDiff = display.contains("c");
    displayPropertyDiff = display.contains("p");
    maxValue = RepoUtil.getMaxResultsOption(cli, CommonOptions.OPT_MAXRESULTS, Messages.DiffCmd_MaxResultNumberFormatException, 512);
    



    if ((display.length() > 2) || 
      ((display.length() == 2) && ((!displayContentDiff) || (!displayPropertyDiff))) || (
      (!displayContentDiff) && (!displayPropertyDiff))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_INVALID_OPTION_VALUE, 
        display, DiffCmdOpts.OPT_DISPLAY.toString()));
    }
    
    if ((displayPropertyDiff) && (cli.hasOption(DiffCmdOpts.OPT_EXTERNAL_COMPARE))) {
      throw StatusHelper.argSyntax(Messages.DiffCmd_CANNOT_COMPARE_EXTERNAL_PROPERTY_DIFF);
    }
    


    if ((cli.getOption(DiffCmdOpts.OPT_COMPONENT, null) != null) && (
      (after.getType().equalsIgnoreCase("file")) || 
      (after.getType().equalsIgnoreCase("changeset")))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.DiffCmd_CANNOT_SPECIFY_COMPONENT, 
        DiffCmdOpts.OPT_COMPONENT.toString()));
    }
    
    if (cli.hasOption(DiffCmdOpts.OPT_DIFF_FILE_SELECTOR)) {
      changeSelectors = cli.getOptions(DiffCmdOpts.OPT_DIFF_FILE_SELECTOR);
      
      if ((cli.hasOption(DiffCmdOpts.OPT_EXTERNAL_COMPARE)) && (changeSelectors.size() > 1)) {
        throw StatusHelper.inappropriateArgument(Messages.DiffCmd_EXTERNAL_ONE_FILE);
      }
      

      if ((after != null) && (after.getType().equalsIgnoreCase("file"))) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.DiffCmd_FILE_MUTUALLYEXCLUSIVE, DiffCmdOpts.OPT_DIFF_FILE_SELECTOR.getLongOpt(), 
          "file"));
      }
    } else if ((!after.getType().equalsIgnoreCase("file")) && (cli.hasOption(DiffCmdOpts.OPT_EXTERNAL_COMPARE)))
    {
      throw StatusHelper.inappropriateArgument(Messages.DiffCmd_EXTERNAL_ONLY_FILE);
    }
    

    client = SubcommandUtil.setupDaemon(config);
    
    try
    {
      if (after.getType().equalsIgnoreCase("file"))
        singleFileDiff(config, after, before, fullPatch, cli.hasOption(DiffCmdOpts.OPT_EXTERNAL_COMPARE));
      for (;;) { return;
        
        if ((after.getType().equalsIgnoreCase("workspace")) || 
          (after.getType().equalsIgnoreCase("stream")) || 
          (after.getType().equalsIgnoreCase("snapshot")) || 
          (after.getType().equalsIgnoreCase("baseline"))) {
          contextDiff(config, after, before, changeSelectors, fullPatch);
        }
        else {
          if (!after.getType().equalsIgnoreCase("changeset")) break;
          changesetDiff(config, after, before, fullPatch, 
            wsSelector != null ? wsSelector.getItemSelector() : null, changeSelectors);
        }
      }
      
      if (!hasMoreItems) break label1051; } finally { if ((hasMoreItems) && (!config.isJSONEnabled())) {
        config.getContext().stdout().println(NLS.bind(Messages.DiffCmd_SpeficyMaxResultOrAHigherValue, 
          cli.getDefinition().getOption(CommonOptions.OPT_MAXRESULTS).getName()));
      }
    }
    if (!config.isJSONEnabled()) {
      config.getContext().stdout().println(NLS.bind(Messages.DiffCmd_SpeficyMaxResultOrAHigherValue, 
        cli.getDefinition().getOption(CommonOptions.OPT_MAXRESULTS).getName()));
    }
    
    label1051:
    
    throw StatusHelper.argSyntax(NLS.bind(Messages.DiffCmd_87, after.getType()));
  }
  




  private Set<String> getMisMatchedChangeSelectors(List<String> changeSelectors, Map<String, String> pathTochangeItemId, List<String> changeItemIds)
    throws FileSystemException
  {
    Set<String> misMatchedChangeItemIds = null;
    String itemUUID = null;
    IUuidAliasRegistry.IUuidAlias alias = null;
    if (changeSelectors != null) {
      misMatchedChangeItemIds = new HashSet();
      for (String change : changeSelectors) {
        alias = RepoUtil.lookupUuidAndAlias(change);
        if (alias != null) {
          itemUUID = alias.getUuid().getUuidValue();
          if (pathTochangeItemId.containsValue(itemUUID)) {
            changeItemIds.add(itemUUID);
          } else {
            misMatchedChangeItemIds.add(change);
          }
        } else {
          ILocation changePath = SubcommandUtil.makeAbsolutePath(config, change);
          if (pathTochangeItemId.get(changePath.toOSString()) != null) {
            changeItemIds.add((String)pathTochangeItemId.get(changePath.toOSString()));
          } else {
            misMatchedChangeItemIds.add(change);
          }
        }
      }
    }
    
    return misMatchedChangeItemIds;
  }
  
  private void updatePathToChangeItemIdMap(Map<String, String> pathTochangeItemIdMap, ChangeSetSyncDTO csDTO) throws FileSystemException {
    String pathHint = null;
    if (pathTochangeItemIdMap != null) {
      List<ChangeFolderSyncDTOImpl> nextChanges = csDTO.getChanges();
      Iterator localIterator2; for (Iterator localIterator1 = nextChanges.iterator(); localIterator1.hasNext(); 
          localIterator2.hasNext())
      {
        ChangeFolderSyncDTOImpl next = (ChangeFolderSyncDTOImpl)localIterator1.next();
        localIterator2 = next.getChanges().iterator(); continue;ChangeSyncDTO changeSyncDTO = (ChangeSyncDTO)localIterator2.next();
        pathHint = changeSyncDTO.getPathHint();
        if (pathHint != null)
        {
          if (pathHint.contains("<unresolved>")) {
            pathHint = pathHint.replace("<unresolved>", "");
          }
          ILocation changePath = SubcommandUtil.makeAbsolutePath(config, pathHint);
          pathTochangeItemIdMap.put(changePath.toOSString(), changeSyncDTO.getVersionableItemId());
        }
      }
    }
  }
  



  private FileState getFileStateFromChangeHistory(ITeamRepository repo, IChangeHistory history, IVersionableHandle verHandle, IScmClientConfiguration config)
    throws FileSystemException
  {
    try
    {
      changeHistories = history.getHistoryFor(verHandle, 1, true, null);
    } catch (TeamRepositoryException e) { List<IChangeHistoryEntryChange> changeHistories;
      throw StatusHelper.wrap(Messages.DiffCmd_31, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    List<IChangeHistoryEntryChange> changeHistories;
    if (changeHistories.isEmpty()) {
      return FileState.getDeletedState(verHandle.getItemType(), null);
    }
    
    IChangeHistoryEntryChange changeHistory = (IChangeHistoryEntryChange)changeHistories.get(0);
    return getFileStateFromChangeSet(repo, changeHistory.changeSet(), verHandle, config);
  }
  

  private FileState getFileStateFromChangeSet(ITeamRepository repo, IChangeSetHandle csHandle, IVersionableHandle verHandle, IScmClientConfiguration config)
    throws FileSystemException
  {
    try
    {
      cs = 
        (IChangeSet)repo.itemManager().fetchCompleteItem(
        csHandle, 0, null);
    } catch (TeamRepositoryException e) { IChangeSet cs;
      throw StatusHelper.wrap(Messages.DiffCmd_32, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    IChangeSet cs;
    IVersionableHandle stateHandle = null;
    for (IChange change : cs.changes()) {
      if (change.item().getItemId().equals(verHandle.getItemId())) {
        stateHandle = change.afterState();
        break;
      }
    }
    if (stateHandle == null) {
      throw StatusHelper.itemNotFound(Messages.DiffCmd_ItemNotFound_Changeset);
    }
    
    try
    {
      afterState = 
        SCMPlatform.getWorkspaceManager(repo).versionableManager().fetchCompleteState(
        stateHandle, null);
    } catch (TeamRepositoryException e) { IVersionable afterState;
      throw StatusHelper.wrap(Messages.DiffCmd_33, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    IVersionable afterState;
    return ClientFileStateFactory.create(repo, stateHandle.getItemType(), afterState);
  }
  





















  private IBaselineHandle getBaseline(IBaselineSet snapshot, ITeamRepository repo, String componentId)
    throws FileSystemException
  {
    IBaselineHandle blHandle = null;
    List<IBaseline> baselines = RepoUtil.getExistingAccessibleItems(
      IBaseline.ITEM_TYPE, snapshot.getBaselines(), repo, config);
    for (IBaseline baseline : baselines) {
      if (componentId.equals(baseline.getComponent().getItemId().getUuidValue())) {
        blHandle = baseline;
        break;
      }
    }
    return blHandle;
  }
  
  private void singleFileDiff(IScmClientConfiguration config, StateSelector after, StateSelector before, boolean fullPatch, boolean externalCompare)
    throws FileSystemException
  {
    String externalCompareTool = null;
    if (externalCompare) {
      externalCompareTool = getExternalCompareTool((ScmClientConfiguration)config);
    }
    

    boolean isDefaultSelector = false;
    
    if (before != null) {
      if ((!before.getType().equalsIgnoreCase("baseline")) && 
        (!before.getType().equalsIgnoreCase("snapshot")) && 
        (!before.getType().equalsIgnoreCase("changeset")) && 
        (!before.getType().equalsIgnoreCase("workspace")) && 
        (!before.getType().equalsIgnoreCase("stream")))
      {
        throw StatusHelper.argSyntax(NLS.bind(Messages.DiffCmd_UNSUPPORTED_BEFORE_TYPE, before.getType(), 
          after.getType()));
      }
    }
    else {
      isDefaultSelector = true;
    }
    
    ILocation absolutePath = SubcommandUtil.makeAbsolutePath(config, after.getItemSelector());
    




    ResourcePropertiesDTO resourceProperties = RepoUtil.getResourceProperties(
      absolutePath.toOSString(), SubcommandUtil.shouldRefreshFileSystem(config), client, 
      config, false);
    IRelativeLocation relativeLocation = new RelativeLocation(resourceProperties.getPath().getRelativePath().getSegments());
    
    ShareDTO share = resourceProperties.getShare();
    ITeamRepository repo = RepoUtil.getTeamRepository(UUID.valueOf(share.getRepositoryId()));
    ParmsWorkspace afterWs = new ParmsWorkspace(repo.getRepositoryURI(), share.getContextItemId());
    
    String versionableItemId = resourceProperties.getItemId();
    String versionableItemType = resourceProperties.getVersionableItemType();
    boolean afterStateDeleted = false;
    String defaultBeforeStateId = null;
    boolean defaultBeforeStateDeleted = false;
    

    SyncViewDTO syncView = SubcommandUtil.getSyncView(Collections.singletonList(afterWs), true, client, config);
    
    WorkspaceSyncDTO workspaceSync = SubcommandUtil.getWorkspaceSync(syncView, share.getContextItemId());
    
    if (workspaceSync == null) {
      throw StatusHelper.unexpectedFailure(NLS.bind(Messages.DiffCmd_UNABLE_TO_DETERMINE_WS, 
        absolutePath.toOSString()));
    }
    ComponentSyncDTO componentSync = SubcommandUtil.getComponentSync(workspaceSync, share.getComponentItemId());
    
    if (componentSync == null) {
      throw StatusHelper.unexpectedFailure(NLS.bind(
        Messages.DiffCmd_UNABLE_TO_DETERMINE_COMP, absolutePath.toOSString()));
    }
    

    LocalChangeSyncDTO localChangeSync = versionableItemId != null ? 
      SubcommandUtil.getLocalChangeSync(componentSync, versionableItemId) : 
      SubcommandUtil.getLocalChangeSync(componentSync, relativeLocation);
    if (localChangeSync != null) {
      if (localChangeSync.isDeletionType())
      {
        versionableItemId = localChangeSync.getTargetVersionableItemId();
        versionableItemType = localChangeSync.getVersionableItemType();
        afterStateDeleted = true;
      } else if (localChangeSync.isAdditionType())
      {
        versionableItemId = localChangeSync
          .getTargetVersionableItemId() != null ? localChangeSync
          .getTargetVersionableItemId() : 
          ItemId.getNullItemUUID().getUuidValue();
        defaultBeforeStateDeleted = true;
      }
      

      if (!defaultBeforeStateDeleted) {
        defaultBeforeStateId = localChangeSync.getTargetVersionableStateId();
      }
    }
    else
    {
      ChangeSyncDTO changeSync = versionableItemId != null ? 
        SubcommandUtil.getChangeSync(componentSync, versionableItemId, true) : 
        SubcommandUtil.getChangeSync(componentSync, relativeLocation, true);
      if (changeSync != null) {
        if (changeSync.isDeleteType())
        {
          versionableItemId = changeSync.getVersionableItemId();
          versionableItemType = changeSync.getVersionableItemType();
          afterStateDeleted = true;
        } else if (changeSync.isAddType())
        {
          defaultBeforeStateDeleted = true;
        }
        

        if (!defaultBeforeStateDeleted) {
          defaultBeforeStateId = changeSync.getBeforeStateId();
        }
      }
    }
    




    if ((versionableItemId == null) || (versionableItemType == null)) {
      throw StatusHelper.ambiguousSelector(NLS.bind(Messages.Common_PATH_DOES_NOT_EXIST, absolutePath.toOSString()));
    }
    
    if ((externalCompare) && (!versionableItemType.equals("file"))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AnnotateCmd_MUST_BE_FILE, absolutePath.toOSString()));
    }
    
    IItemType itemType = SubcommandUtil.getVersionableItemType(versionableItemType);
    
    IVersionableHandle verHandle = (IVersionableHandle)itemType.createItemHandle(UUID.valueOf(versionableItemId), null);
    
    SiloedItemId<IVersionable> siloedVer = SiloedItemId.create(itemType, UUID.valueOf(versionableItemId), 
      UUID.valueOf(share.getComponentItemId()));
    FileState afterState = null;
    FileState beforeState = null;
    IConnection beforeSelectorConn = null;
    
    if (afterStateDeleted)
    {
      afterState = FileState.getDeletedState(itemType, null);
    } else if (localChangeSync != null)
    {

      IShareable shareable = SharingManager.getInstance().findShareable(absolutePath, ResourceType.FILE);
      afterState = FileStateFactory.create(shareable, null);



    }
    else
    {


      IVersionableHandle stateHandle = RepoUtil.getVersionableHandle(repo, versionableItemId, resourceProperties.getStateId(), 
        versionableItemType, config);
      try {
        IVersionable versionable = SCMPlatform.getWorkspaceManager(repo).versionableManager().fetchCompleteState(stateHandle, null);
        afterState = ClientFileStateFactory.create(repo, versionable.getItemType(), versionable);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.DiffCmd_33, e, new IndentingPrintStream(config.getContext().stderr()), 
          repo.getRepositoryURI());
      }
    }
    
    if (isDefaultSelector) {
      if (defaultBeforeStateDeleted)
      {


        beforeState = FileState.getDeletedState(itemType, null);
      } else if (defaultBeforeStateId != null)
      {


        IVersionableHandle stateHandle = RepoUtil.getVersionableHandle(repo, versionableItemId, defaultBeforeStateId, 
          versionableItemType, config);
        try {
          IVersionable versionable = SCMPlatform.getWorkspaceManager(repo).versionableManager().fetchCompleteState(stateHandle, null);
          beforeState = ClientFileStateFactory.create(repo, versionable.getItemType(), versionable);
        } catch (TeamRepositoryException e) {
          throw StatusHelper.wrap(Messages.DiffCmd_33, e, new IndentingPrintStream(config.getContext().stderr()), 
            repo.getRepositoryURI());
        }
      }
      else
      {
        beforeState = afterState;
      }
    } else if ((before.getType().equalsIgnoreCase("baseline")) || 
      (before.getType().equalsIgnoreCase("snapshot")))
    {
      ITeamRepository beforeRepo = RepoUtil.loginUrlArgAncestor(config, client, selector);
      IBaselineHandle blHandle = null;
      
      if (before.getType().equalsIgnoreCase("snapshot")) {
        IBaselineSet snapshot = RepoUtil.getSnapshot(null, before.getItemSelector(), beforeRepo, config);
        blHandle = getBaseline(snapshot, beforeRepo, share.getComponentItemId());
        if (blHandle == null) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.DiffCmd_COMPONENT_NOT_FOUND_IN_SNAPSHOT, 
            share.getComponentName(), relativeLocation.toString()));
        }
      } else {
        blHandle = RepoUtil.getBaseline(before.getItemSelector(), share.getComponentItemId(), 
          share.getComponentName(), beforeRepo, client, config);
      }
      IBaselineConnection blConn = getBlConn(blHandle.getItemId().getUuidValue(), beforeRepo.getRepositoryURI(), config);
      beforeState = getFileStateFromChangeHistory(beforeRepo, blConn.changeHistory(), verHandle, config);
      beforeSelectorConn = blConn;
    } else if (before.getType().equalsIgnoreCase("changeset"))
    {
      ITeamRepository beforeRepo = RepoUtil.loginUrlArgAncestor(config, client, selector);
      IChangeSet changeSet = RepoUtil.findChangeSet(before.getItemSelector(), beforeRepo, config);
      beforeState = getFileStateFromChangeSet(beforeRepo, changeSet, verHandle, config);
    }
    else {
      ITeamRepository beforeRepo = RepoUtil.loginUrlArgAncestor(config, client, selector);
      String wsId = before.getItemSelector();
      boolean isWorkspaceSelector = before.getType().equalsIgnoreCase("workspace");
      
      IWorkspace wsHandle = RepoUtil.getWorkspace(wsId, isWorkspaceSelector, !isWorkspaceSelector, beforeRepo, config);
      ParmsWorkspace beforeWs = new ParmsWorkspace(beforeRepo.getRepositoryURI(), wsHandle.getItemId().getUuidValue());
      IComponentHandle compHandle = RepoUtil.getComponent(share.getComponentItemId(), beforeRepo, config);
      IWorkspaceConnection wsConn = getWsConn(beforeWs, config);
      try {
        beforeState = getFileStateFromChangeHistory(beforeRepo, wsConn.changeHistory(compHandle), verHandle, config);
      } catch (ItemNotFoundException localItemNotFoundException) {
        throw StatusHelper.itemNotFound(Messages.DiffCmd_28);
      } catch (ComponentNotInWorkspaceException localComponentNotInWorkspaceException) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.DiffCmd_29, share.getComponentName(), 
          relativeLocation.toString()));
      }
      beforeSelectorConn = wsConn;
    }
    
    if (externalCompare) {
      AbstractOpenInExternalCompareOperation externalCompareOperation = null;
      if (!afterStateDeleted) {
        IShareable shareable = SharingManager.getInstance().findShareable(absolutePath, ResourceType.FILE);
        externalCompareOperation = new OpenShareableInExternalCompareOperation(
          externalCompareTool, shareable, beforeState, null, true);
      } else {
        externalCompareOperation = new OpenFileItemInExternalCompareOperation(
          externalCompareTool, afterState, null, beforeState, null, null, true);
      }
      try
      {
        externalCompareOperation.setEnvironment(config.getContext().environment());
        externalCompareOperation.run(null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.failure(Messages.DiffCmd_ERROR_EXTERNAL_COMPARE, e);
      }
      
      if (externalCompareOperation.returnValue() != 0) {
        throw StatusHelper.returnValue(externalCompareOperation.returnValue());
      }
      return;
    }
    

    FileChange fileChange = new FileChange(beforeState, afterState, siloedVer);
    ConfigurationChange confChange = new ConfigurationChange();
    confChange.addChange(fileChange);
    

    ChangeDescription changeDesc = 
      new ChangeDescription(
      "", Collections.EMPTY_LIST, 
      new ItemId(IComponent.ITEM_TYPE, UUID.valueOf(share.getComponentItemId())), 
      Collections.EMPTY_LIST, new Date());
    IPathResolver afterPathResolver = new RemotePathResolver(getWsConn(afterWs, config));
    IPathResolver beforePathResolver = beforeSelectorConn != null ? new RemotePathResolver(
      beforeSelectorConn) : null;
    
    doDiff(afterPathResolver, beforePathResolver, confChange, changeDesc, fullPatch, config);
  }
  
  private IWorkspaceConnection getWsConn(ParmsWorkspace ws, IScmClientConfiguration config) throws FileSystemException {
    IWorkspaceConnection wsConn = null;
    if (ws != null) {
      try {
        wsConn = ws.getWorkspaceConnection(null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.DiffCmd_84, e, new IndentingPrintStream(config.getContext().stderr()), 
          repositoryUrl);
      }
    }
    
    return wsConn;
  }
  
  private IBaselineConnection getBlConn(String baselineItemId, String repoUri, IScmClientConfiguration config) throws FileSystemException {
    IBaselineConnection blConn = null;
    ParmsBaseline bl = new ParmsBaseline();
    baselineItemId = baselineItemId;
    repositoryUrl = repoUri;
    try {
      blConn = bl.getBaselineConnection(null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.DiffCmd_26, e, new IndentingPrintStream(config.getContext().stderr()), 
        repositoryUrl);
    }
    return blConn;
  }
  
  private IBaseline getBaseline(String baselineSelector, ITeamRepository repo) throws FileSystemException
  {
    IBaseline baseline = (IBaseline)baselineMap.get(baselineSelector);
    if (baseline != null) {
      return baseline;
    }
    
    if (componentItemSelector == null) {
      IUuidAliasRegistry.IUuidAlias blAlias = RepoUtil.lookupUuidAndAlias(baselineSelector, repo.getRepositoryURI());
      
      if (blAlias != null) {
        BaselineDTO blDTO = RepoUtil.getBaselineById(blAlias.getUuid().getUuidValue(), 
          repo.getRepositoryURI(), client, config);
        
        if (blDTO == null) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.CompareCmd_UNMATCHED_BASELINE_OR_COMP_MISSING, 
            baselineSelector, DiffCmdOpts.OPT_COMPONENT.getLongOpt()));
        }
        baseline = (IBaseline)RepoUtil.getItem(IBaseline.ITEM_TYPE, UUID.valueOf(blDTO.getItemId()), 
          repo, config);
      }
      else
      {
        throw StatusHelper.argSyntax(Messages.CompareCmd_SPECIFY_COMPONENT);
      }
    }
    else {
      IUuidAliasRegistry.IUuidAlias blAlias = RepoUtil.lookupUuidAndAlias(baselineSelector, repo.getRepositoryURI());
      
      if (blAlias != null) {
        BaselineDTO blDTO = RepoUtil.getBaselineById(blAlias.getUuid().getUuidValue(), 
          repo.getRepositoryURI(), client, config);
        if (blDTO != null) {
          baseline = (IBaseline)RepoUtil.getItem(IBaseline.ITEM_TYPE, UUID.valueOf(blDTO.getItemId()), repo, 
            config);
        }
      }
      

      if (baseline == null) {
        baseline = RepoUtil.getBaseline(baselineSelector, component.getItemId().getUuidValue(), 
          component.getName(), repo, client, config);
      }
    }
    
    baselineMap.put(baselineSelector, baseline);
    return baseline;
  }
  

  private ContextDiffInput getContextDiffInput(String itemSelector, String contextType, ITeamRepository repo, boolean resolveItem)
    throws FileSystemException
  {
    resolveItem = (resolveItem) || (!RepoUtil.isUuid(itemSelector));
    boolean hasComponent = true;
    ContextDiffInput contextDiffInput = null;
    
    if ("snapshot".equalsIgnoreCase(contextType)) {
      IBaselineSetHandle snapshot = null;
      if ((resolveItem) || (component != null)) {
        snapshot = RepoUtil.getSnapshot(null, itemSelector, repo, config);
        if (component != null) {
          hasComponent = getBaseline((IBaselineSet)snapshot, repo, component.getItemId().getUuidValue()) != null;
        }
      } else {
        snapshot = (IBaselineSetHandle)IBaselineSet.ITEM_TYPE.createItemHandle(repo, 
          UUID.valueOf(itemSelector), null);
      }
      ParmsContext context = new ParmsContext("baselineset", repo.getRepositoryURI(), 
        snapshot.getItemId().getUuidValue());
      IPathResolver pathResolver = SnapshotPathResolver.create(SnapshotId.getSnapshotId(repo, snapshot));
      contextDiffInput = new ContextDiffInput(context, pathResolver, hasComponent, contextType);
    } else if ("baseline".equalsIgnoreCase(contextType)) {
      if ((resolveItem) || (component != null)) {
        IBaseline baseline = getBaseline(itemSelector, repo);
        itemSelector = baseline.getItemId().getUuidValue();
        if (component != null) {
          hasComponent = baseline.getComponent().getItemId().equals(component.getItemId());
        }
      }
      ParmsContext context = new ParmsContext("baseline", repo.getRepositoryURI(), 
        itemSelector);
      IPathResolver pathResolver = new RemotePathResolver(getBlConn(itemSelector, repo.getRepositoryURI(), config));
      contextDiffInput = new ContextDiffInput(context, pathResolver, hasComponent, contextType);
    } else {
      if (resolveItem) {
        boolean isWorkspace = "workspace".equalsIgnoreCase(contextType);
        IWorkspace workspace = RepoUtil.getWorkspace(itemSelector, isWorkspace, !isWorkspace, repo, config);
        itemSelector = workspace.getItemId().getUuidValue();
      }
      ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), itemSelector);
      ParmsContext context = new ParmsContext("workspace", repositoryUrl, 
        workspaceItemId);
      IWorkspaceConnection wsConn = getWsConn(ws, config);
      IPathResolver pathResolver = new RemotePathResolver(wsConn);
      if (component != null) {
        IComponentInfo compInfo = null;
        try {
          compInfo = wsConn.getComponentInfo(component);
        }
        catch (ComponentNotInWorkspaceException localComponentNotInWorkspaceException) {}
        
        hasComponent = compInfo != null;
      }
      contextDiffInput = new ContextDiffInput(context, pathResolver, hasComponent, contextType);
    }
    return contextDiffInput;
  }
  


  private void validateComponentSelector(StateSelector after, StateSelector before, ITeamRepository afterRepo, ITeamRepository beforeRepo)
    throws FileSystemException
  {
    IScmCommandLineArgument componentSelector = ScmCommandLineArgument.create(config.getSubcommandCommandLine().getOptionValue(DiffCmdOpts.OPT_COMPONENT, null), config);
    SubcommandUtil.validateArgument(componentSelector, RepoUtil.ItemType.COMPONENT);
    

    if (componentSelector != null) {
      componentItemSelector = componentSelector.getItemSelector();
      ITeamRepository componentRepo = afterRepo;
      try
      {
        component = RepoUtil.getComponent(componentItemSelector, componentRepo, config);
        

        if ((beforeRepo != null) && 
          (!RepoUtil.isRepoUriSame(afterRepo.getRepositoryURI(), beforeRepo.getRepositoryURI(), config))) {
          componentRepo = beforeRepo;
          
          component = ((IComponent)RepoUtil.getItem(IComponent.ITEM_TYPE, component.getItemId(), 
            componentRepo, config));
        }
      }
      catch (CLIFileSystemClientException e) {
        if (e.getStatus().getCode() == 25) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.DiffCmd_COMP_NOT_FOUND, componentItemSelector, 
            componentRepo.getRepositoryURI()));
        }
        if (e.getStatus().getCode() == 9) {
          throw StatusHelper.ambiguousSelector(NLS.bind(Messages.DiffCmd_AMBIGUOUS_COMP, componentItemSelector, 
            componentRepo.getRepositoryURI()));
        }
        throw e;
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Component in context: " + component.getName() + component.getItemId());
      }
    } else if ((before != null) && (
      (type.equalsIgnoreCase("baseline")) || 
      (type.equalsIgnoreCase("baseline"))))
    {













      StateSelector baselineSelector = before;
      ITeamRepository baselineRepo = beforeRepo;
      ITeamRepository otherRepo = afterRepo;
      if (type.equalsIgnoreCase("baseline")) {
        baselineSelector = after;
        baselineRepo = afterRepo;
        otherRepo = beforeRepo;
      }
      IBaseline baseline = getBaseline(baselineSelector.getItemSelector(), baselineRepo);
      ITeamRepository componentRepo = baselineRepo;
      try {
        component = ((IComponent)RepoUtil.getItem(IComponent.ITEM_TYPE, baseline.getComponent().getItemId(), 
          componentRepo, config));
        if (!RepoUtil.isRepoUriSame(baselineRepo.getRepositoryURI(), otherRepo.getRepositoryURI(), config)) {
          componentRepo = otherRepo;
          component = ((IComponent)RepoUtil.getItem(IComponent.ITEM_TYPE, 
            baseline.getComponent().getItemId(), componentRepo, config));
        }
      }
      catch (CLIFileSystemClientException e) {
        if (e.getStatus().getCode() == 25) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.DiffCmd_COMP_BL_NOT_FOUND, new String[] {
            baseline.getComponent().getItemId().getUuidValue(), baselineSelector.getItemSelector(), 
            componentRepo.getRepositoryURI() }));
        }
        throw e;
      }
      compBaselineSelector = baselineSelector.getItemSelector();
      if (logger.isDebugEnabled()) {
        logger.debug("Component in context: " + component.getName() + component.getItemId());
        logger.debug("Baseline in context: " + compBaselineSelector);
      }
    }
  }
  
  private void failMissingComponent(StateSelector stateSelector, ContextDiffInput context) throws FileSystemException
  {
    if (componentItemSelector != null)
    {
      if (type.equalsIgnoreCase("baseline")) {
        IBaseline baseline = (IBaseline)baselineMap.get(stateSelector.getItemSelector());
        String componentId = baseline != null ? baseline.getComponent().getItemId().getUuidValue() : null;
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.DiffCmd_BL_NOT_BELONG_TO_COMP, new String[] {
          stateSelector.getItemSelector(), componentId, componentItemSelector }));
      }
      if (type.equalsIgnoreCase("snapshot"))
      {
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.DiffCmd_COMP_NOT_FOUND_IN_SS, 
          new String[] { stateSelector.getItemSelector(), componentItemSelector })); }
      if (type.equalsIgnoreCase("stream"))
      {
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.DiffCmd_COMP_NOT_FOUND_IN_ST, 
          new String[] { stateSelector.getItemSelector(), componentItemSelector }));
      }
      
      throw StatusHelper.inappropriateArgument(NLS.bind(
        Messages.DiffCmd_COMP_NOT_FOUND_IN_WS, 
        new String[] { stateSelector.getItemSelector(), componentItemSelector }));
    }
    
    if (compBaselineSelector != null)
    {


      if (type.equalsIgnoreCase("baseline")) {
        IBaseline baseline = (IBaseline)baselineMap.get(stateSelector.getItemSelector());
        String componentId = baseline != null ? baseline.getComponent().getItemId().getUuidValue() : null;
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.DiffCmd_BL_NOT_BELONG_TO_COMP_BL, 
          new String[] { stateSelector.getItemSelector(), componentId, 
          component.getItemId().getUuidValue(), compBaselineSelector }));
      }
      if (type.equalsIgnoreCase("snapshot"))
      {

        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.DiffCmd_COMP_BL_NOT_FOUND_IN_SS, 
          new String[] { stateSelector.getItemSelector(), 
          component.getItemId().getUuidValue(), compBaselineSelector })); }
      if (type.equalsIgnoreCase("stream"))
      {

        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.DiffCmd_COMP_BL_NOT_FOUND_IN_ST, 
          new String[] { stateSelector.getItemSelector(), 
          component.getItemId().getUuidValue(), compBaselineSelector }));
      }
      

      throw StatusHelper.inappropriateArgument(NLS.bind(
        Messages.DiffCmd_COMP_BL_NOT_FOUND_IN_WS, 
        new String[] { stateSelector.getItemSelector(), 
        component.getItemId().getUuidValue(), compBaselineSelector }));
    }
  }
  


  private void failMissingComponent(StateSelector after, ContextDiffInput afterContext, StateSelector before, ContextDiffInput beforeContext)
    throws FileSystemException
  {
    boolean afterHasComponent = hasComponent;
    boolean beforeHasComponent = hasComponent;
    
    if (!afterHasComponent) {
      failMissingComponent(after, afterContext);
    } else if ((before != null) && (!beforeHasComponent)) {
      failMissingComponent(before, beforeContext);
    } else if (!beforeHasComponent)
    {





      if (componentItemSelector != null) {
        if (type.equalsIgnoreCase("snapshot")) {
          if (type.equalsIgnoreCase("stream")) {
            throw StatusHelper.inappropriateArgument(NLS.bind(
              Messages.DiffCmd_COMP_NOT_IN_ST_FOR_SS, new String[] {
              context.itemId, after.getItemSelector(), 
              componentItemSelector }));
          }
          
          throw StatusHelper.inappropriateArgument(NLS.bind(
            Messages.DiffCmd_COMP_NOT_IN_WS_FOR_SS, new String[] {
            context.itemId, after.getItemSelector(), 
            componentItemSelector }));
        }
        
        if (type.equalsIgnoreCase("stream")) {
          throw StatusHelper.inappropriateArgument(NLS.bind(
            Messages.DiffCmd_COMP_NOT_IN_COLLAB_WS_ST, new String[] {
            context.itemId, after.getItemSelector(), 
            componentItemSelector }));
        }
        
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.DiffCmd_COMP_NOT_IN_COLLAB_WS_WS, new String[] {
          context.itemId, after.getItemSelector(), 
          componentItemSelector }));
      }
    }
  }
  



  private void contextDiff(IScmClientConfiguration config, StateSelector after, StateSelector before, List<String> changeSelectors, boolean fullPatch)
    throws FileSystemException
  {
    if ((before != null) && 
      (!before.getType().equalsIgnoreCase("workspace")) && 
      (!before.getType().equalsIgnoreCase("stream")) && 
      (!before.getType().equalsIgnoreCase("snapshot")) && 
      (!before.getType().equalsIgnoreCase("baseline")))
    {
      throw StatusHelper.argSyntax(NLS.bind(Messages.DiffCmd_UNSUPPORTED_BEFORE_TYPE, before.getType(), 
        after.getType()));
    }
    
    ITeamRepository afterRepo = RepoUtil.loginUrlArgAncestor(config, client, after.getSelector());
    ITeamRepository beforeRepo = null;
    if (before != null) {
      beforeRepo = RepoUtil.loginUrlArgAncestor(config, client, before.getSelector());
    }
    validateComponentSelector(after, before, afterRepo, beforeRepo);
    
    ContextDiffInput afterContext = null;
    ContextDiffInput beforeContext = null;
    if (before != null) {
      afterContext = getContextDiffInput(after.getItemSelector(), type, afterRepo, true);
      beforeContext = getContextDiffInput(before.getItemSelector(), type, beforeRepo, true);
    } else if (type.equalsIgnoreCase("snapshot"))
    {

      IBaselineSet snapshot = RepoUtil.getSnapshot(null, after.getItemSelector(), afterRepo, config);
      if (logger.isDebugEnabled()) {
        logger.debug("Snapshot in context: " + after.getItemSelector());
        logger.debug("Snapshot owner: " + snapshot.getOwner().getItemId());
      }
      
      IWorkspace beforeWs = (IWorkspace)RepoUtil.getItem(IWorkspace.ITEM_TYPE, snapshot.getOwner().getItemId(), afterRepo, config);
      if (logger.isDebugEnabled()) {
        logger.debug("Snapshot owner(name): " + beforeWs.getName());
      }
      String beforeType = beforeWs.isStream() ? "stream" : "workspace";
      
      afterContext = getContextDiffInput(snapshot.getItemId().getUuidValue(), type, afterRepo, false);
      beforeContext = getContextDiffInput(beforeWs.getItemId().getUuidValue(), beforeType, afterRepo, false);
    } else if (type.equalsIgnoreCase("baseline"))
    {

      IBaselineHandle baseline = getBaseline(after.getItemSelector(), afterRepo);
      IBaselineConnection blConn = getBlConn(baseline.getItemId().getUuidValue(), 
        afterRepo.getRepositoryURI(), config);
      IBaselineHandle prevBaseline = null;
      try
      {
        IHistoricBaselineIterator blIter = blConn.getBaselinesInHistory(1, null);
        if (blIter.getBaselines().isEmpty())
        {
          config.getContext().stdout().println(Messages.DiffCmd_NO_PREV_BL);
          return;
        }
        prevBaseline = (IBaselineHandle)blIter.getBaselines().get(0);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.DiffCmd_UNABLE_TO_FETCH_BL_HIST, e, 
          new IndentingPrintStream(config.getContext().stderr()), afterRepo.getRepositoryURI());
      }
      afterContext = getContextDiffInput(baseline.getItemId().getUuidValue(), type, afterRepo, false);
      if (logger.isDebugEnabled()) {
        logger.debug("Baseline: " + after.getItemSelector());
        logger.debug("Previous baseline " + prevBaseline.getItemId());
      }
      beforeContext = getContextDiffInput(prevBaseline.getItemId().getUuidValue(), type, afterRepo, false);

    }
    else
    {
      boolean isWorkspace = after.getType().equalsIgnoreCase("workspace");
      IWorkspace afterWsFound = RepoUtil.getWorkspace(after.getItemSelector(), isWorkspace, !isWorkspace, afterRepo, config);
      ParmsWorkspace afterWs = new ParmsWorkspace(afterRepo.getRepositoryURI(), afterWsFound.getItemId()
        .getUuidValue());
      
      WorkspaceDetailsDTO afterWsDetails = 
        (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(afterWs), client, config).get(0);
      ParmsWorkspace wsFlow = RepoUtil.getFlowTarget(afterWsDetails, true, false);
      
      if (wsFlow == null) {
        config.getContext().stdout().println(Messages.DiffCmd_NO_COLLABORATION);
        return;
      }
      beforeRepo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
      
      IWorkspace beforeWs = (IWorkspace)RepoUtil.getItem(IWorkspace.ITEM_TYPE, 
        UUID.valueOf(workspaceItemId), beforeRepo, config);
      if (logger.isDebugEnabled()) {
        logger.debug(type + " in context: " + after.getItemSelector());
        logger.debug("Current flow target: " + beforeWs.getName() + beforeWs.getItemId());
      }
      String beforeType = beforeWs.isStream() ? "stream" : "workspace";
      
      afterContext = getContextDiffInput(workspaceItemId, type, afterRepo, false);
      beforeContext = getContextDiffInput(workspaceItemId, beforeType, beforeRepo, false);
    }
    


    if ((component != null) && ((!hasComponent) || (!hasComponent))) {
      failMissingComponent(after, afterContext, before, beforeContext);
    }
    
    List<ItemId<IChangeSet>> outgoingChanges = null;
    List<ItemId<IChangeSet>> incomingChanges = null;
    String componentId = component != null ? component.getItemId().getUuidValue() : null;
    try {
      outgoingChanges = SubcommandUtil.compareAndGetChangeSetItemIds(context, 
        context, componentId, client, false);
      incomingChanges = SubcommandUtil.compareAndGetChangeSetItemIds(context, 
        context, componentId, client, true);
    } catch (TeamRepositoryException tre) {
      StatusHelper.wrap(Messages.DiffCmd_FAILED_WHEN_DET_DIFF, tre, new IndentingPrintStream(config.getContext().stderr()));
    }
    
    if ((outgoingChanges.isEmpty()) && (incomingChanges.isEmpty())) {
      if (changeSelectors == null) {
        config.getContext().stdout().println(Messages.DiffCmd_NO_DIFFERENCES);
      } else {
        config.getContext().stdout().println(Messages.DiffCmd_NO_DIFFERENCES_FILE_SELECTOR);
      }
      
      return;
    }
    
    List<String> changeItemIds = null;
    
    if (changeSelectors != null) {
      ChangeSetSyncDTO csDTO = null;
      String itemType = null;
      changeItemIds = new ArrayList(changeSelectors.size());
      Map<String, String> pathTochangeItemIdMap = new HashMap();
      
      for (ItemId<IChangeSet> cs : incomingChanges) {
        itemType = type;
        if (itemType.equals("stream")) {
          itemType = "workspace";
        }
        csDTO = RepoUtil.findChangeSet(cs.getItemUUID().getUuidValue(), true, RepoUtil.lookupUuidAndAlias(context.itemId).getUuid().getUuidValue(), itemType, 
          beforeRepo == null ? afterRepo.getRepositoryURI() : beforeRepo.getRepositoryURI(), client, config);
        updatePathToChangeItemIdMap(pathTochangeItemIdMap, csDTO);
      }
      
      for (ItemId<IChangeSet> cs : outgoingChanges) {
        itemType = type;
        if (itemType.equals("stream")) {
          itemType = "workspace";
        }
        csDTO = RepoUtil.findChangeSet(cs.getItemUUID().getUuidValue(), true, RepoUtil.lookupUuidAndAlias(context.itemId).getUuid().getUuidValue(), itemType, 
          afterRepo.getRepositoryURI(), client, config);
        updatePathToChangeItemIdMap(pathTochangeItemIdMap, csDTO);
      }
      
      Set<String> mismatchedChangeSelectors = getMisMatchedChangeSelectors(changeSelectors, pathTochangeItemIdMap, changeItemIds);
      
      if ((mismatchedChangeSelectors != null) && (mismatchedChangeSelectors.size() > 0)) {
        IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
        err.println(Messages.DiffCmd_NO_DIFFERENCES_CS);
        for (String selector : mismatchedChangeSelectors) {
          err.indent().println(selector);
        }
        throw StatusHelper.inappropriateArgument(Messages.DiffCmd_NOT_IN_WS_STREAM_HELP);
      }
    }
    


    try
    {
      ConfigurationChange outgoingConfChange = ClientConfigurationChangeFactory.createChangeForHandles(afterRepo, outgoingChanges, changeItemIds, 
        null);
      ConfigurationChange incomingConfChange = ClientConfigurationChangeFactory.createChangeForHandles(
        beforeRepo == null ? afterRepo : beforeRepo, incomingChanges, changeItemIds, null);
      changeDesc = ConfigurationChangeFactory.getDescriptionForHandles(
        outgoingChanges.isEmpty() ? beforeRepo : beforeRepo == null ? afterRepo : afterRepo, 
        outgoingChanges.isEmpty() ? incomingChanges : outgoingChanges, null);
    } catch (TeamRepositoryException e) { ChangeDescription changeDesc;
      throw StatusHelper.wrap(Messages.DiffCmd_79, e, new IndentingPrintStream(config.getContext().stderr())); }
    ChangeDescription changeDesc;
    ConfigurationChange outgoingConfChange;
    ConfigurationChange incomingConfChange; ConfigurationChange confChange = outgoingConfChange.merge(incomingConfChange.reverse());
    doDiff(pathResolver, pathResolver, confChange, changeDesc, fullPatch, config);
  }
  
  private void changesetDiff(IScmClientConfiguration config, StateSelector after, StateSelector before, boolean fullPatch, String wsSelector, List<String> changeSelectors)
    throws FileSystemException
  {
    IScmCommandLineArgument afterSelector = after.getSelector();
    ChangeSetSyncDTO csDTO = null;
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, afterSelector);
    

    ParmsWorkspace ws = null;
    if (wsSelector != null) {
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector, true, true, repo, config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
      csDTO = RepoUtil.findChangeSet(afterSelector.getItemSelector(), true, workspaceItemId, "workspace", 
        repo.getRepositoryURI(), client, config);
    } else {
      csDTO = RepoUtil.findChangeSet(afterSelector.getItemSelector(), true, null, null, 
        repo.getRepositoryURI(), client, config);
    }
    

    ChangeSetSyncDTO beforeCsDTO = null;
    if (before != null) {
      ITeamRepository repo2 = RepoUtil.loginUrlArgAncestor(config, client, before.getSelector());
      

      if (repo.getId() != repo2.getId()) {
        StatusHelper.argSyntax(Messages.DiffCmd_CHANGESETS_REPO_NOTSAME);
      }
      
      if (before.getType().equalsIgnoreCase("changeset")) {
        if (ws != null) {
          beforeCsDTO = RepoUtil.findChangeSet(before.getItemSelector(), true, workspaceItemId, "workspace", 
            repo2.getRepositoryURI(), client, config);
        } else {
          beforeCsDTO = RepoUtil.findChangeSet(before.getItemSelector(), true, null, null, 
            repo2.getRepositoryURI(), client, config);
        }
      }
      else {
        throw StatusHelper.argSyntax(NLS.bind(Messages.DiffCmd_UNSUPPORTED_BEFORE_TYPE, before.getType(), after.getType()));
      }
    }
    
    List<ItemId<IChangeSet>> csHandles = new LinkedList();
    ItemId<IChangeSet> afterCsId = null;
    List<String> changeItemIds = null;
    if (changeSelectors != null) {
      changeItemIds = new ArrayList();
    }
    
    Map<String, String> pathTochangeItemIdMap = new HashMap();
    
    if ((beforeCsDTO == null) || (csDTO.getLastChangeDate() > beforeCsDTO.getLastChangeDate())) {
      afterCsId = new ItemId(IChangeSet.ITEM_TYPE, UUID.valueOf(csDTO.getChangeSetItemId()));
      if (changeSelectors != null) {
        updatePathToChangeItemIdMap(pathTochangeItemIdMap, csDTO);
      }
    } else {
      afterCsId = new ItemId(IChangeSet.ITEM_TYPE, UUID.valueOf(beforeCsDTO.getChangeSetItemId()));
      if (changeSelectors != null) {
        updatePathToChangeItemIdMap(pathTochangeItemIdMap, beforeCsDTO);
      }
    }
    
    Set<String> mismatchedChangeSelectors = getMisMatchedChangeSelectors(changeSelectors, pathTochangeItemIdMap, changeItemIds);
    

    if ((mismatchedChangeSelectors != null) && (mismatchedChangeSelectors.size() > 0)) {
      if ((!RepoUtil.isSandboxPath(SubcommandUtil.makeAbsolutePath(config, (String)changeSelectors.get(0)).toOSString(), client, config)) && (ws == null)) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.DiffCmd_PROVIDE_WS, DiffCmdOpts.OPT_WORKSPACE.getLongOpt()));
      }
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      err.println(Messages.DiffCmd_NOT_FOUND_IN_CS);
      for (String selector : mismatchedChangeSelectors) {
        err.indent().println(selector);
      }
      
      ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, 
        ListChangesCmd.class);
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.DiffCmd_NOT_IN_CS_HELP, new String[] {
        config.getContext().getAppName(), 
        SubcommandUtil.getExecutionString(defnTemp).toString() }));
    }
    
    csHandles.add(afterCsId);
    

    try
    {
      ConfigurationChange confChange = ClientConfigurationChangeFactory.createChangeForHandles(repo, csHandles, changeItemIds, null);
      changeDesc = ConfigurationChangeFactory.getDescriptionForHandles(repo, csHandles, null);
    } catch (TeamRepositoryException e) { ChangeDescription changeDesc;
      throw StatusHelper.wrap(Messages.DiffCmd_79, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI()); }
    ChangeDescription changeDesc;
    ConfigurationChange confChange;
    if (beforeCsDTO != null) {
      csHandles.clear();
      
      Object csList = null;
      if (csDTO.getLastChangeDate() > beforeCsDTO.getLastChangeDate()) {
        csList = findChangeSetsInBetween(csDTO, beforeCsDTO, ws, repo);
      } else {
        csList = findChangeSetsInBetween(beforeCsDTO, csDTO, ws, repo);
      }
      csHandles.addAll((Collection)csList);
      try
      {
        ConfigurationChange confChange1 = ClientConfigurationChangeFactory.createChangeForHandles(repo, csHandles, null);
        confChange = confChange.merge(confChange1);
        ChangeDescription changeDesc1 = ConfigurationChangeFactory.getDescriptionForHandles(repo, csHandles, null);
        changeDesc = changeDesc.merge(changeDesc1);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.DiffCmd_79, e, new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
    }
    
    IPathResolver pathResolver = ws != null ? new RemotePathResolver(getWsConn(ws, config)) : null;
    doDiff(pathResolver, null, confChange, changeDesc, fullPatch, config);
  }
  
  private List<ItemId<IChangeSet>> findChangeSetsInBetween(ChangeSetSyncDTO csDTO, ChangeSetSyncDTO beforeCsDTO, ParmsWorkspace ws, ITeamRepository repo)
    throws FileSystemException
  {
    List<ItemId<IChangeSet>> csList = new ArrayList();
    

    Map<String, ChangeSyncDTO> beforeVerItemsMap = new HashMap();
    Iterator localIterator2; for (Iterator localIterator1 = beforeCsDTO.getChanges().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      ChangeFolderSyncDTO changeFolderSyncDTO = (ChangeFolderSyncDTO)localIterator1.next();
      localIterator2 = changeFolderSyncDTO.getChanges().iterator(); continue;changeDTO = (ChangeSyncDTO)localIterator2.next();
      beforeVerItemsMap.put(changeDTO.getVersionableItemId(), changeDTO);
    }
    


    Set<ChangeSyncDTO> matchingChanges = new HashSet();
    Iterator localIterator3; ChangeSyncDTO beforeChangeDTO; for (ChangeSyncDTO changeDTO = csDTO.getChanges().iterator(); changeDTO.hasNext(); 
        localIterator3.hasNext())
    {
      ChangeFolderSyncDTO changeFolderSyncDTO = (ChangeFolderSyncDTO)changeDTO.next();
      localIterator3 = changeFolderSyncDTO.getChanges().iterator(); continue;ChangeSyncDTO changeDTO = (ChangeSyncDTO)localIterator3.next();
      if (beforeVerItemsMap.containsKey(changeDTO.getVersionableItemId()))
      {

        beforeChangeDTO = (ChangeSyncDTO)beforeVerItemsMap.get(changeDTO.getVersionableItemId());
        if (!beforeChangeDTO.getAfterStateId().equals(changeDTO.getBeforeStateId())) {
          matchingChanges.add(changeDTO);
        }
      }
    }
    


    for (changeDTO = matchingChanges.iterator(); changeDTO.hasNext(); 
        


        beforeChangeDTO.hasNext())
    {
      ChangeSyncDTO changeDTO = (ChangeSyncDTO)changeDTO.next();
      Object csIdList = findChangeSetsInBetweenDates(repo, ws != null ? workspaceItemId : null, 
        csDTO.getComponentItemId(), changeDTO.getVersionableItemId(), changeDTO.getVersionableItemType(), 
        csDTO.getLastChangeDate(), beforeCsDTO.getLastChangeDate());
      beforeChangeDTO = ((List)csIdList).iterator(); continue;String csId = (String)beforeChangeDTO.next();
      ItemId<IChangeSet> csItemId = new ItemId(IChangeSet.ITEM_TYPE, UUID.valueOf(csId));
      csList.add(csItemId);
    }
    

    return csList;
  }
  
  private List<String> findChangeSetsInBetweenDates(ITeamRepository repo, String wsId, String compId, String verId, String verType, long beforeDate, long afterDate) throws FileSystemException
  {
    IScmRestService.ParmsChangeSetSearchCriteria csCriteria = new IScmRestService.ParmsChangeSetSearchCriteria();
    if (wsId != null) {
      contextHandleItemId = wsId;
      contextType = "workspace";
    }
    componentItemId = compId;
    versionableItemId = verId;
    versionableItemType = verType;
    versionableItemTypeNamespace = SubcommandUtil.getVersionableItemType(verType).getNamespaceURI();
    modifiedBeforeTimestamp = Long.valueOf(beforeDate);
    modifiedAfterTimestamp = Long.valueOf(afterDate);
    
    List<String> csList = new ArrayList();
    IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
    
    boolean value = ListChangesetsCmd.getChangeSets(scmService, csCriteria, csList, repo.getRepositoryURI(), maxValue, config, client, config.getSubcommandCommandLine());
    if (value)
    {
      hasMoreItems = value;
    }
    
    return csList;
  }
  

  private void doDiff(IPathResolver pathResolver1, IPathResolver pathResolver2, ConfigurationChange confChange, ChangeDescription changeDesc, boolean fullPatch, IScmClientConfiguration config)
    throws FileSystemException
  {
    List<IPathResolver> resolvers = new LinkedList();
    resolvers.add(CopyFileAreaPathResolver.create());
    if (pathResolver1 != null) {
      resolvers.add(pathResolver1);
    }
    if (pathResolver2 != null) {
      resolvers.add(pathResolver2);
    }
    IPathResolver resolver = new FallbackPathResolver(resolvers);
    

    if (fullPatch) {
      try {
        CreatePatchUtil.createPatch(
          new CreatePatchDilemmaHandler() {}, config.getContext().stdout(), confChange, changeDesc, 
          resolver, "UTF-8", null);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.DiffCmd_22, e, new IndentingPrintStream(config.getContext().stderr()));
      }
    }
    else {
      try {
        paths = ResolvedConfigurationChangePaths.resolve(resolver, confChange, null);
      } catch (TeamRepositoryException e) { ResolvedConfigurationChangePaths paths;
        Log log = LogFactory.getLog(DiffCmd.class.getName());
        log.error(e);
        throw StatusHelper.failure(Messages.ERROR_CHECK_LOG, null); }
      ResolvedConfigurationChangePaths paths;
      final OutputStream stdOut = config.getContext().stdout();
      StringDiffParticipant diffParticipant = new StringDiffParticipant()
      {
        protected void writeHeader(String header) throws IOException
        {
          hasContentDiff = true;
          stdOut.write(header.getBytes());
        }
        
        public void writeHunkRange(HunkRange range) throws IOException
        {
          stdOut.write((range.toString() + "\n").getBytes());
        }
        
        public void writeCommonLine(List<String> range, int lineNum) throws IOException
        {
          stdOut.write(" ".getBytes());
          stdOut.write(((String)range.get(lineNum)).getBytes());
        }
        
        public void writeAddedLine(List<String> range, int lineNum) throws IOException
        {
          stdOut.write("+".getBytes());
          stdOut.write(((String)range.get(lineNum)).getBytes());
        }
        
        public void writeRemovedLine(List<String> range, int lineNum) throws IOException
        {
          stdOut.write("-".getBytes());
          stdOut.write(((String)range.get(lineNum)).getBytes());
        }
        
        public void writeNoTrailingNL(List<String> range) throws IOException
        {
          stdOut.write("\n\\ No newline at end of file\n".getBytes());
        }
      };
      for (FileChange fileChange : confChange.getChanges()) {
        try
        {
          hasContentDiff = false;
          if (displayContentDiff) {
            CreateDiffUtil.writeDiff(diffParticipant, fileChange, paths, null);
          }
          if ((displayPropertyDiff) && (
            (fileChange.hasPropertyChange()) || 
            (!fileChange.isModify())))
          {




            writePropertyDiff(stdOut, fileChange, paths, hasContentDiff);
          }
        } catch (IOException e) {
          if ((e.getCause() instanceof VersionedContentDeleted)) {
            String path = getFilePath(fileChange, paths, config);
            config.getContext().stdout().println(NLS.bind(Messages.DiffCmd_DELETED_CONTENT, path));
          } else if ((e.getCause() instanceof VersionablePermissionDeniedException)) {
            String path = getFilePath(fileChange, paths, config);
            config.getContext().stdout().println(NLS.bind(Messages.Common_VERSIONABLE_ITEM_PERMISSSION_DENIED, path));
          } else {
            throw StatusHelper.failure(Messages.DiffCmd_22, e);
          }
        } catch (TeamRepositoryException e) {
          throw StatusHelper.wrap(Messages.DiffCmd_22, e, new IndentingPrintStream(config.getContext().stderr()));
        }
      }
    }
  }
  
  private String getFilePath(FileChange fileChange, ResolvedConfigurationChangePaths paths, IScmClientConfiguration config) throws FileSystemException
  {
    String path = null;
    try {
      if (paths != null)
      {
        path = paths.computePath(fileChange.getSiloedItemId(), false, null).toPath().toString();
        if (path == null) {
          path = paths.computePath(fileChange.getSiloedItemId(), true, null).toPath().toString();
        }
      }
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.DiffCmd_23, e, new IndentingPrintStream(config.getContext().stderr()));
    }
    

    if (path == null) {
      path = fileChange.getItemId().getItemUUID().getUuidValue();
    }
    
    return path;
  }
  

  private void writePropertyDiff(OutputStream out, FileChange change, ResolvedConfigurationChangePaths paths, boolean hasContentDiff)
    throws IOException, TeamRepositoryException
  {
    FileState beforeState = change.getInitial();
    FileState afterState = change.getFinal();
    Map<String, String> beforeStateProperties = beforeState.getProperties();
    Map<String, String> afterStateProperties = afterState.getProperties();
    TreeSet<String> propertyNames = new TreeSet(beforeStateProperties.keySet());
    propertyNames.addAll(afterStateProperties.keySet());
    
    if ((!change.isModify()) && (!change.getItemId().getItemType().equals(IFileItem.ITEM_TYPE)) && 
      (propertyNames.size() == 0)) {
      return;
    }
    String diffHeader;
    if (!hasContentDiff) {
      String beforePath = null;
      String afterPath = null;
      if (paths != null) {
        if (!beforeState.isDeleted()) {
          beforePath = 
            paths.computePath(change.getSiloedItemId(), true, null).toPath().toString();
        }
        if (!afterState.isDeleted()) {
          afterPath = 
            paths.computePath(change.getSiloedItemId(), false, null).toPath().toString();
        }
      }
      diffHeader = CreateDiffUtil.getFilePatchHeader(
        beforePath, afterPath, 
        change.getInitial().getTimestamp(), 
        change.getFinal().getTimestamp());
      
      out.write(diffHeader.getBytes());
    }
    
    Map<String, PropertyModificationType> modifiedProperties = new HashMap();
    
    for (String name : propertyNames) {
      String initialValue = (String)beforeStateProperties.get(name);
      String finalValue = (String)afterStateProperties.get(name);
      if (finalValue == null) {
        modifiedProperties.put(name, PropertyModificationType.DELETE_PROPERTY);
      } else if (initialValue == null) {
        modifiedProperties.put(name, PropertyModificationType.ADD_PROPERTY);
      } else if (!initialValue.equals(finalValue)) {
        modifiedProperties.put(name, PropertyModificationType.MODIFY_PROPERTY);
      }
    }
    String finalContentType;
    if (change.getItemId().getItemType().equals(IFileItem.ITEM_TYPE))
    {
      if (!change.isModify()) {
        PropertyModificationType modType = change.getInitial().isDeleted() ? PropertyModificationType.ADD_PROPERTY : 
          PropertyModificationType.DELETE_PROPERTY;
        modifiedProperties.put("jazz.executable", modType);
        modifiedProperties.put("jazz.mime", modType);
        modifiedProperties.put("jazz.line-delimiter", modType);
      } else {
        boolean initialExecutable = beforeState.isExectuable();
        boolean finalExecutable = afterState.isExectuable();
        if (initialExecutable != finalExecutable) {
          modifiedProperties.put("jazz.executable", PropertyModificationType.MODIFY_PROPERTY);
        }
        String initialContentType = beforeState.getContentType();
        finalContentType = afterState.getContentType();
        if (!initialContentType.equals(finalContentType)) {
          modifiedProperties.put("jazz.mime", PropertyModificationType.MODIFY_PROPERTY);
        }
        FileLineDelimiter initialLineDelimeter = beforeState.getLineDelimiter();
        FileLineDelimiter finalLineDelimeter = afterState.getLineDelimiter();
        if (initialLineDelimeter != finalLineDelimeter) {
          modifiedProperties.put("jazz.line-delimiter", PropertyModificationType.MODIFY_PROPERTY);
        }
      }
    }
    
    TreeSet<String> modifiedPropertyNames = new TreeSet(modifiedProperties.keySet());
    
    int maxWidth = 0;
    for (String propertyName : modifiedPropertyNames) {
      maxWidth = Math.max(maxWidth, propertyName.length());
    }
    
    out.write((Messages.ConflictsCmd_Properties + "\n").getBytes());
    
    for (String name : modifiedPropertyNames) {
      if (((PropertyModificationType)modifiedProperties.get(name)).equals(PropertyModificationType.DELETE_PROPERTY)) {
        writePropertyDiffLine(out, maxWidth, true, name, (String)beforeStateProperties.get(name), beforeState);
      } else if (((PropertyModificationType)modifiedProperties.get(name)).equals(PropertyModificationType.ADD_PROPERTY)) {
        writePropertyDiffLine(out, maxWidth, false, name, (String)afterStateProperties.get(name), afterState);
      } else if (((PropertyModificationType)modifiedProperties.get(name)).equals(PropertyModificationType.MODIFY_PROPERTY)) {
        writePropertyDiffLine(out, maxWidth, true, name, (String)beforeStateProperties.get(name), beforeState);
        writePropertyDiffLine(out, maxWidth, false, name, (String)afterStateProperties.get(name), afterState);
      }
    }
  }
  
  private void writePropertyDiffLine(OutputStream out, int maxWidth, boolean writeBeforeState, String name, String value, FileState fileState) throws IOException
  {
    if (value != null) {
      writePropertyDiffLine(out, maxWidth, writeBeforeState, name, value);
    } else if (name.equals("jazz.executable")) {
      writePropertyDiffLine(out, maxWidth, writeBeforeState, name, Boolean.toString(fileState.isExectuable()));
    } else if (name.equals("jazz.mime")) {
      writePropertyDiffLine(out, maxWidth, writeBeforeState, name, fileState.getContentType());
    } else if (name.equals("jazz.line-delimiter")) {
      writePropertyDiffLine(out, maxWidth, writeBeforeState, name, fileState.getLineDelimiter().toString());
    }
  }
  
  private void writePropertyDiffLine(OutputStream out, int maxWidth, boolean writeBeforeState, String name, String value)
    throws IOException
  {
    out.write((writeBeforeState ? "-" : "+").getBytes());
    out.write(
      (NLS.bind(Messages.PropertyListCmd_KeyValue, StringUtil.pad(name, maxWidth), value) + "\n").getBytes());
  }
  
  public static String getExternalCompareTool(ScmClientConfiguration config) throws FileSystemException
  {
    return getExternalCompareTool(config, false);
  }
  
  public static String getExternalCompareTool(ScmClientConfiguration config, boolean compare3way) throws FileSystemException
  {
    ITypedPreferenceRegistry prefs = config.getPersistentPreferences();
    String externalCompareTool = null;
    if (compare3way) {
      externalCompareTool = prefs.getExternal3wayCompareTool();
    }
    

    if (externalCompareTool == null) {
      externalCompareTool = prefs.getExternal2wayCompareTool();
    }
    
    if (externalCompareTool == null) {
      throw StatusHelper.inappropriateArgument(Messages.DiffCmd_NO_EXTERNAL_COMPARE_TOOL_SET);
    }
    
    if (!isValidExecutable(externalCompareTool)) {
      throw StatusHelper.inappropriateArgument(NLS.bind(
        Messages.DiffCmd_INVALID_EXTERNAL_COMPARE_TOOL, externalCompareTool));
    }
    
    return externalCompareTool;
  }
  






  private static boolean isValidExecutable(String commandLine)
  {
    isValid = false;
    String[] commandArray = ExternalCompareToolsUtil.createCommandArray(commandLine);
    if (commandArray.length > 0) {
      String filePath = commandArray[0];
      if ((filePath == null) || (filePath.length() == 0)) {
        isValid = false;
      } else {
        try {
          try {
            isValid = SubcommandUtil.exists(new Path(filePath), null);
          } catch (FileSystemException localFileSystemException) {
            isValid = false;
          }
          





          return isValid;
        }
        catch (SecurityException localSecurityException)
        {
          isValid = false;
        }
      }
    }
  }
}
