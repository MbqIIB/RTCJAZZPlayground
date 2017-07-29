package com.ibm.team.filesystem.cli.client.internal.lock;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLock;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLockDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLockResources;
import com.ibm.team.filesystem.client.rest.parameters.ParmsVersionable;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.ISymbolicLink;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.locks.LockResourcesResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.LockOperationFailureException;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import com.ibm.team.scm.common.internal.rest.dto.WorkspaceDTO;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.osgi.util.NLS;






public class LockAcquireCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  private static final PositionalOptionDefinition OPT_FILES = new PositionalOptionDefinition("files", 0, -1);
  private static final NamedOptionDefinition OPT_COMPONENT = new NamedOptionDefinition("c", "component", 1);
  private static final NamedOptionDefinition OPT_REMOTE_PATH = new NamedOptionDefinition("R", "remotePath", 0);
  private static final NamedOptionDefinition OPT_IGNORE_INCOMING = new NamedOptionDefinition("I", "ignore-incoming", 0);
  private String filesHelpMsg;
  private String lockingFolderPathMsg;
  
  public static class LockItem
  {
    String componentId;
    String versionableId;
    String versionableType;
    
    public LockItem(String componentId, String versionableId, String versionableType) {
      this.componentId = componentId;
      this.versionableId = versionableId;
      this.versionableType = versionableType;
    }
    
    public String getComponent() {
      return componentId;
    }
    
    public String getVersionable() {
      return versionableId;
    }
    
    public String getVersionableType() {
      return versionableType;
    }
  }
  
  public LockAcquireCmd()
  {
    this(NLS.bind(Messages.LockAcquireCmd_OPT_FILES_HELP, OPT_REMOTE_PATH.getName()), Messages.LockAcquireCmd_LockingFolderPathIsUnsupported);
  }
  

  protected LockAcquireCmd(String filesHelpMsg, String lockingFolderPathMsg)
  {
    this.filesHelpMsg = filesHelpMsg;
    this.lockingFolderPathMsg = lockingFolderPathMsg;
  }
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    addNamedOptions(options);
    addPositionalOptions(options);
    return options;
  }
  
  protected void addNamedOptions(Options options) throws ConflictingOptionException {
    SubcommandUtil.addRepoLocationToOptions(options);
    options.addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS);
    options.addOption(OPT_IGNORE_INCOMING, Messages.LockAcquireCmd_OPT_IGNORE_INCOMING_HELP);
    options.addOption(new SimpleGroup(false)
      .addOption(OPT_REMOTE_PATH, Messages.LockAcquireCmd_OPT_REMOTEPATH_HELP, false)
      .addOption(CommonOptions.OPT_STREAM_SELECTOR, NLS.bind(Messages.LockAcquireCmd_OPT_STREAM_HELP, OPT_REMOTE_PATH.getName()), true)
      .addOption(OPT_COMPONENT, NLS.bind(Messages.LockAcquireCmd_OPT_COMPONENT_HELP, OPT_REMOTE_PATH.getName()), false));
    options.addOption(CommonOptions.OPT_COMPONENT_HIERARCHY, Messages.LockAcquireCmd_OPT_COMPONENT_HIERARCHY_HELP);
  }
  
  protected void addPositionalOptions(Options options) throws ConflictingOptionException {
    options.addOption(OPT_FILES, filesHelpMsg);
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if ((cli.hasOption(OPT_REMOTE_PATH)) && ((!cli.hasOption(CommonOptions.OPT_STREAM_SELECTOR)) || (!cli.hasOption(OPT_COMPONENT)) || (!cli.hasOption(OPT_FILES))))
    {
      throw StatusHelper.argSyntax(NLS.bind(
        Messages.LockAcquireCmd_SPECIFY_STREAM_AND_COMPONENT, 
        new Object[] {
        CommonOptions.OPT_STREAM_SELECTOR.getName(), 
        OPT_COMPONENT.getName(), 
        OPT_FILES.getName(), 
        OPT_REMOTE_PATH.getName() }));
    }
    

    if ((cli.hasOption(OPT_FILES)) && (!cli.hasOption(OPT_REMOTE_PATH)) && ((cli.hasOption(CommonOptions.OPT_STREAM_SELECTOR)) || (cli.hasOption(OPT_COMPONENT))))
    {
      throw StatusHelper.argSyntax(NLS.bind(
        Messages.LockAcquireCmd_1, 
        new Object[] {
        OPT_FILES.getName(), 
        OPT_REMOTE_PATH.getName(), 
        CommonOptions.OPT_STREAM_SELECTOR.getName(), 
        OPT_COMPONENT.getName() }));
    }
    

    if ((!cli.hasOption(OPT_FILES)) && (!cli.hasOption(CommonOptions.OPT_STREAM_SELECTOR)))
    {
      throw StatusHelper.argSyntax(NLS.bind(
        Messages.LockAcquireCmd_2, 
        new Object[] {
        CommonOptions.OPT_STREAM_SELECTOR.getName(), 
        OPT_FILES.getName() }));
    }
    

    boolean isStreamLock = (!cli.hasOption(OPT_FILES)) && (!cli.hasOption(OPT_COMPONENT));
    boolean isComponentLock = (!isStreamLock) && (!cli.hasOption(OPT_FILES));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    List<String> strPaths = null;
    if (cli.hasOption(OPT_FILES)) {
      List<IScmCommandLineArgument> paths = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_FILES), config);
      SubcommandUtil.validateArgument(paths, RepoUtil.ItemType.VERSIONABLE);
      strPaths = RepoUtil.getSelectors(paths);
    }
    IWorkspace workspace;
    if (cli.hasOption(CommonOptions.OPT_STREAM_SELECTOR)) {
      IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_STREAM_SELECTOR), config);
      SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.STREAM);
      

      ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      
      workspace = RepoUtil.getWorkspace(wsSelector.getItemSelector(), false, true, repo, config);
      ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), workspace.getItemId().getUuidValue());
      
      if (isStreamLock) {
        lock(ws, client, config, cli);
      }
      else {
        IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT), config);
        SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
        
        WorkspaceComponentDTO comp = RepoUtil.getComponent(ws, compSelector.getItemSelector(), client, config);
        
        if (isComponentLock) {
          lock(ws, comp, cli.hasOption(CommonOptions.OPT_COMPONENT_HIERARCHY), client, config, cli);
        }
        else {
          List<LockItem> lockItems = getRemoteLockItems(workspace, comp, strPaths, repo, config);
          
          Map<ParmsWorkspace, List<LockItem>> streamToLockItems = new HashMap();
          Map<ParmsWorkspace, ParmsWorkspace> streamToWorkspace = new HashMap();
          streamToLockItems.put(ws, lockItems);
          lock(streamToLockItems, streamToWorkspace, client, config, cli);
        }
      }
    }
    else
    {
      List<ILocation> absolutePaths = SubcommandUtil.makeAbsolutePaths(config, strPaths);
      
      for (ILocation path : absolutePaths) {
        if (!SubcommandUtil.exists(path, null)) {
          throw StatusHelper.inappropriateArgument(NLS.bind(
            Messages.Common_PATH_DOES_NOT_EXIST, path.toOSString()));
        }
      }
      
      List<ResourcePropertiesDTO> resProps = RepoUtil.getResourceProperties(strPaths, 
        SubcommandUtil.shouldRefreshFileSystem(config), client, config, true);
      

      Map<ParmsWorkspace, List<LockItem>> streamToLockItems = getLocalLockItems(resProps, client, config);
      
      Map<ParmsWorkspace, ParmsWorkspace> streamToWorkspace = getStreamToWorkspace(resProps, client, config);
      lock(streamToLockItems, streamToWorkspace, client, config, cli);
    }
  }
  
  protected void lock(ParmsWorkspace ws, WorkspaceComponentDTO comp, boolean lockComponentHierarchy, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli) throws CLIFileSystemClientException, FileSystemException
  {
    try
    {
      ParmsLock lock = new ParmsLock();
      workspace = ws;
      componentItemId = comp.getItemId();
      lockComponentHierarchy = lockComponentHierarchy;
      ParmsLockResources parms = new ParmsLockResources();
      locks = new ParmsLock[] { lock };
      client.postLockResources(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(null, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.LockAcquireCmd_LOCKS_SUCCESSFULLY_ACQUIRED);
  }
  
  protected void lock(ParmsWorkspace ws, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli) throws CLIFileSystemClientException, FileSystemException
  {
    try {
      ParmsLock lock = new ParmsLock();
      workspace = ws;
      ParmsLockResources parms = new ParmsLockResources();
      locks = new ParmsLock[] { lock };
      client.postLockResources(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(null, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    
    config.getContext().stdout().println(Messages.LockAcquireCmd_LOCKS_SUCCESSFULLY_ACQUIRED);
  }
  
  private List<LockItem> getRemoteLockItems(IWorkspace workspace, WorkspaceComponentDTO comp, List<String> strPaths, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
    List<LockItem> lockItems = new ArrayList();
    
    for (String strPath : strPaths) {
      IUuidAliasRegistry.IUuidAlias lockAlias = RepoUtil.lookupUuidAndAlias(strPath, repo.getRepositoryURI());
      VersionableDTO versionable = null;
      if (lockAlias != null) {
        versionable = getVersionableById(scmService, workspace.getItemId().getUuidValue(), comp.getItemId(), lockAlias.getUuid().getUuidValue(), config);
        if (versionable == null) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.Common_INVALID_ALIAS_UUID, strPath));
        }
      } else {
        String[] pathSeg = StringUtil.splitEscapedPath(strPath);
        String path = StringUtil.createPathString(pathSeg);
        versionable = RepoUtil.getVersionableByPath(scmService, 
          workspace.getItemId().getUuidValue(), comp.getItemId(), path, config);
      }
      

      String verType = SubcommandUtil.getVersionableItemType(versionable.getVersionable().getItemType());
      if (verType.equals("folder")) {
        throw StatusHelper.argSyntax(NLS.bind(lockingFolderPathMsg, strPath));
      }
      
      LockItem lockItem = new LockItem(versionable.getComponent().getItemId().getUuidValue(), 
        versionable.getVersionable().getItemId().getUuidValue(), verType);
      lockItems.add(lockItem);
    }
    
    return lockItems;
  }
  
  public VersionableDTO getVersionableById(IScmRestService scmService, String wsId, String compId, String verId, IScmClientConfiguration config)
    throws FileSystemException
  {
    VersionableDTO verDTO = null;
    
    String itemType = IFileItem.ITEM_TYPE.getNamespaceURI() + "." + IFileItem.ITEM_TYPE.getName();
    try {
      return RepoUtil.getVersionableById(scmService, wsId, compId, verId, null, itemType, config);

    }
    catch (Exception localException1)
    {

      itemType = ISymbolicLink.ITEM_TYPE.getNamespaceURI() + "." + ISymbolicLink.ITEM_TYPE.getName();
      try {
        return RepoUtil.getVersionableById(scmService, wsId, compId, verId, null, itemType, config);
      }
      catch (Exception localException2) {}
    }
    

    return null;
  }
  
  private Map<ParmsWorkspace, List<LockItem>> getLocalLockItems(List<ResourcePropertiesDTO> resProps, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Map<ParmsWorkspace, List<LockItem>> streamToLockItems = new HashMap();
    

    Map<String, ParmsWorkspace> wsToStream = new HashMap();
    for (ResourcePropertiesDTO resProp : resProps)
    {
      if (resProp.getVersionableItemType().equals("folder")) {
        throw StatusHelper.argSyntax(NLS.bind(lockingFolderPathMsg, resProp.getFullPath()));
      }
      
      if (resProp.getItemId() == null) {
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.Common_PATH_DOES_NOT_EXIST, resProp.getFullPath()));
      }
      
      ParmsWorkspace targetStream = (ParmsWorkspace)wsToStream.get(resProp.getShare().getContextItemId());
      if (targetStream == null)
      {
        ParmsWorkspace ws = new ParmsWorkspace(RepoUtil.getRepoUri(config, client, 
          resProp.getShare()), resProp.getShare().getContextItemId());
        
        WorkspaceDetailsDTO wsDetails = 
          (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
        
        ITeamRepository targetRepo = null;
        WorkspaceDTO targetWs = null;
        

        ParmsWorkspace flowTarget = RepoUtil.getFlowTarget(wsDetails, false, true);
        if (flowTarget != null) {
          targetRepo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
          targetWs = RepoUtil.getWorkspaceById(workspaceItemId, targetRepo, config);
        }
        

        if ((targetWs == null) || (!targetWs.getWorkspace().isStream())) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.LockAcquireCmd_NO_FLOWTARGET, 
            AliasUtil.selector(wsDetails.getName(), UUID.valueOf(wsDetails.getItemId()), 
            wsDetails.getRepositoryURL(), RepoUtil.ItemType.WORKSPACE)));
        }
        
        targetStream = new ParmsWorkspace(targetRepo.getRepositoryURI(), 
          targetWs.getWorkspace().getItemId().getUuidValue());
        
        wsToStream.put(resProp.getShare().getContextItemId(), targetStream);
      }
      
      List<LockItem> lockItems = (List)streamToLockItems.get(targetStream);
      if (lockItems == null) {
        lockItems = new ArrayList();
        streamToLockItems.put(targetStream, lockItems);
      }
      LockItem lockItem = new LockItem(resProp.getShare().getComponentItemId(), resProp.getItemId(), 
        resProp.getVersionableItemType());
      lockItems.add(lockItem);
    }
    
    return streamToLockItems;
  }
  
  protected void lock(Map<ParmsWorkspace, List<LockItem>> streamToLockItems, Map<ParmsWorkspace, ParmsWorkspace> streamToWorkspace, IFilesystemRestClient client, IScmClientConfiguration config, ICommandLine cli) throws FileSystemException
  {
    for (Map.Entry<ParmsWorkspace, List<LockItem>> entry : streamToLockItems.entrySet()) {
      ParmsWorkspace ws = (ParmsWorkspace)entry.getKey();
      try {
        List<ParmsLock> locks = new ArrayList();
        for (LockItem lockItem : (List)entry.getValue()) {
          ParmsLock lock = new ParmsLock();
          workspace = new ParmsWorkspace();
          workspace.repositoryUrl = repositoryUrl;
          workspace.workspaceItemId = workspaceItemId;
          componentItemId = lockItem.getComponent();
          versionable = new ParmsVersionable();
          versionable.versionableItemType = lockItem.getVersionableType();
          versionable.itemId = lockItem.getVersionable();
          
          for (Map.Entry<ParmsWorkspace, ParmsWorkspace> sourcews : streamToWorkspace.entrySet())
          {
            if (getKeyworkspaceItemId.equals(workspaceItemId)) {
              sourceWorkspace = ((ParmsWorkspace)sourcews.getValue());
            }
          }
          locks.add(lock);
        }
        
        ParmsLockResources parms = new ParmsLockResources();
        locks = ((ParmsLock[])locks.toArray(new ParmsLock[locks.size()]));
        lockDilemmaHandler = new ParmsLockDilemmaHandler();
        lockDilemmaHandler.incomingChangeAffectsFileToLock = (cli.hasOption(OPT_IGNORE_INCOMING) ? "continue" : "cancel");
        LockResourcesResultDTO result = null;
        try
        {
          result = client.postLockResources(parms, null);
        } catch (LockOperationFailureException e) {
          String[] strings = e.getMessage().split("\r\n|\r|\n");
          for (String s : strings) {
            config.getContext().stderr().println(s);
          }
          throw StatusHelper.inappropriateArgument(Messages.LockAcquireCmd_ALREADY_LOCKED);
        }
        if ((result.isCancelled()) && 
          (result.getIncomingChangesToLockedFiles().size() != 0)) {
          throw StatusHelper.createException(
            NLS.bind(Messages.LockAcquireCmd_CONFLICT_WITH_INCOMING_CHANGE, OPT_IGNORE_INCOMING.getName()), 
            63, null);
        }
      }
      catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(null, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
      }
    }
    

    config.getContext().stdout().println(Messages.LockAcquireCmd_LOCKS_SUCCESSFULLY_ACQUIRED);
  }
  
  private Map<ParmsWorkspace, ParmsWorkspace> getStreamToWorkspace(List<ResourcePropertiesDTO> resProps, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Map<ParmsWorkspace, ParmsWorkspace> streamToWorkspace = new HashMap();
    Map<String, ParmsWorkspace> wsToStream = new HashMap();
    for (ResourcePropertiesDTO resProp : resProps)
    {
      if (resProp.getItemId() == null) {
        throw StatusHelper.inappropriateArgument(NLS.bind(
          Messages.Common_PATH_DOES_NOT_EXIST, resProp.getFullPath()));
      }
      
      ParmsWorkspace targetStream = (ParmsWorkspace)wsToStream.get(resProp.getShare().getContextItemId());
      if (targetStream == null)
      {
        ParmsWorkspace ws = new ParmsWorkspace(RepoUtil.getRepoUri(config, client, 
          resProp.getShare()), resProp.getShare().getContextItemId());
        
        WorkspaceDetailsDTO wsDetails = 
          (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
        
        ITeamRepository targetRepo = null;
        WorkspaceDTO targetWs = null;
        

        ParmsWorkspace flowTarget = RepoUtil.getFlowTarget(wsDetails, false, true);
        if (flowTarget != null) {
          targetRepo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
          targetWs = RepoUtil.getWorkspaceById(workspaceItemId, targetRepo, config);
        }
        

        if ((targetWs == null) || (!targetWs.getWorkspace().isStream())) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.LockAcquireCmd_NO_FLOWTARGET, 
            AliasUtil.selector(wsDetails.getName(), UUID.valueOf(wsDetails.getItemId()), 
            wsDetails.getRepositoryURL(), RepoUtil.ItemType.WORKSPACE)));
        }
        
        targetStream = new ParmsWorkspace(targetRepo.getRepositoryURI(), 
          targetWs.getWorkspace().getItemId().getUuidValue());
        
        wsToStream.put(resProp.getShare().getContextItemId(), targetStream);
        streamToWorkspace.put(targetStream, ws);
      }
    }
    
    return streamToWorkspace;
  }
}
