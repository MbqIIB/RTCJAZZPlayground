package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.internal.RelativeLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLoadFileRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLoadFiles;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourceProperties;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.ISymbolicLink;
import com.ibm.team.filesystem.common.ISymbolicLinkHandle;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.client.IVersionableManager;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IFolder;
import com.ibm.team.scm.common.VersionablePermissionDeniedException;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmIntermediateChangeNode;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmIntermediateHistory;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetIntermediateHistory;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;

public class ChangesetExtractCmd extends AbstractSubcommand
{
  public ChangesetExtractCmd() {}
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = null;
    ParmsWorkspace ws = null;
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_WORKSPACE_NAME.getId(), null), config);
    IScmCommandLineArgument csSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetCommonOptions.OPT_CHANGESET.getId()), config);
    
    if (wsSelector != null) {
      ws = RepoUtil.findWorkspaceAndLogin(wsSelector, client, config);
      repo = RepoUtil.getSharedRepository(repositoryUrl, true);
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      

      RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, Collections.singletonList(csSelector), repo, config);
    } else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, csSelector);
      try {
        ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
      }
      catch (Exception localException) {}
    }
    
    SubcommandUtil.validateArgument(csSelector, RepoUtil.ItemType.CHANGESET);
    

    IScmCommandLineArgument itemSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetExtractCmdOpts.OPT_ITEMPATH.getId()), config);
    SubcommandUtil.validateArgument(itemSelector, RepoUtil.ItemType.VERSIONABLE);
    
    String wsId = ws != null ? workspaceItemId : null;
    IRelativeLocation itemPath = null;
    String itemUuid = null;
    
    IUuidAliasRegistry.IUuidAlias itemAlias = RepoUtil.lookupUuidAndAlias(itemSelector.getItemSelector(), repo.getRepositoryURI());
    if (itemAlias != null) {
      itemUuid = itemAlias.getUuid().getUuidValue();
    }
    else {
      ResourcePropertiesDTO itemResource = getResource(itemSelector.getItemSelector(), client, config);
      if (itemResource != null) {
        itemUuid = itemResource.getItemId();
        itemPath = new RelativeLocation(itemResource.getShare().getPath().getSegments());
        if (wsId == null) {
          wsId = itemResource.getShare().getContextItemId();
        }
      } else {
        itemPath = new RelativeLocation(new Path(itemSelector.getItemSelector()).segments());
      }
    }
    

    if ((itemUuid == null) && (ws == null)) {
      throw StatusHelper.argSyntax(Messages.ChangesetExtractCmd_CANNOT_DETERMINE_WS);
    }
    

    String diskPathStr = cli.getOption(ChangesetExtractCmdOpts.OPT_DISKPATH.getId());
    ILocation diskPath = SubcommandUtil.makeAbsolutePath(config, diskPathStr);
    validateDiskPath(diskPath, client, config);
    

    ChangeSetSyncDTO csDTO = RepoUtil.findChangeSet(csSelector.getItemSelector(), true, wsId, 
      "workspace", repo.getRepositoryURI(), client, config);
    

    ChangeSyncDTO matchedChange = getChange(csDTO, itemUuid, itemPath, itemSelector.getItemSelector());
    

    String stateId = null;
    if (cli.hasOption(ChangesetExtractCmdOpts.OPT_VER_STATE)) {
      IScmCommandLineArgument stateSelector = ScmCommandLineArgument.create(cli.getOptionValue(ChangesetExtractCmdOpts.OPT_VER_STATE.getId()), config);
      SubcommandUtil.validateArgument(stateSelector, RepoUtil.ItemType.VERSIONABLE);
      IUuidAliasRegistry.IUuidAlias stateAlias = RepoUtil.lookupUuidAndAlias(stateSelector.getItemSelector(), repo.getRepositoryURI());
      if (stateAlias == null) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.Common_INVALID_ALIAS_UUID, stateSelector.getItemSelector()));
      }
      
      stateId = stateAlias.getUuid().getUuidValue();
    }
    else {
      stateId = matchedChange.getAfterStateId();
    }
    

    validateState(csDTO.getChangeSetItemId(), matchedChange, stateId, repo, config);
    

    String sandboxDiskPath = RepoUtil.getSandboxPath(diskPath.toOSString(), client, config);
    if (sandboxDiskPath != null) {
      ILocation sandboxDiskLoc = new PathLocation(sandboxDiskPath);
      load(sandboxDiskPath, diskPath.getLocationRelativeTo(sandboxDiskLoc).toString(), 
        matchedChange.getVersionableItemId(), stateId, matchedChange.getVersionableItemType(), 
        repo, client, config);
    }
    else {
      extractContent(diskPath, wsId, csDTO.getComponentItemId(), matchedChange.getVersionableItemId(), 
        stateId, matchedChange.getVersionableItemType(), repo, config);
    }
    config.getContext().stdout().println(NLS.bind(Messages.ChangesetExtractCmd_SUCCESS, diskPath.toOSString()));
  }
  
  private ResourcePropertiesDTO getResource(String itemSelector, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    ILocation itemPath = SubcommandUtil.makeAbsolutePath(config, itemSelector);
    try
    {
      RepoUtil.getSandboxPathsAndRegister(itemPath.toOSString(), client, config);
    }
    catch (Exception localException) {}
    

    ParmsResourceProperties parms = new ParmsResourceProperties(false, new String[] { itemPath.toOSString() });
    ResourcesDTO resourcesDTO = null;
    try {
      resourcesDTO = client.getResourceProperties(parms, null);
    }
    catch (TeamRepositoryException localTeamRepositoryException) {}
    

    if (resourcesDTO != null) {
      List<ResourcePropertiesDTO> resourceProperties = resourcesDTO.getResourceProperties();
      ResourcePropertiesDTO dto = (ResourcePropertiesDTO)resourceProperties.get(0);
      if ((dto != null) && (dto.getShare() != null)) {
        return dto;
      }
    }
    
    return null;
  }
  
  private void validateDiskPath(ILocation diskPath, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    String sandboxDiskPath = RepoUtil.getSandboxPath(diskPath.toOSString(), client, config);
    if (sandboxDiskPath != null)
    {
      ILocation sandboxPath = new PathLocation(sandboxDiskPath);
      IRelativeLocation relDiskPath = diskPath.getLocationRelativeTo(sandboxPath);
      if (relDiskPath.segmentCount() == 1) {
        throw StatusHelper.failure(Messages.ChangesetExtractCmd_NOT_A_VALID_PATH, null);
      }
    }
    

    ILocation parentPath = diskPath.getParent();
    if ((!parentPath.isEmpty()) && 
      (!SubcommandUtil.exists(parentPath, null))) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.ChangesetExtractCmd_ITEM_DOES_NOT_EXIST, parentPath.toOSString()));
    }
    

    checkForOverwrite(diskPath, config);
  }
  
  private void checkForOverwrite(ILocation path, IScmClientConfiguration config)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if (cli.hasOption(ChangesetExtractCmdOpts.OPT_OVERWRITE)) {
      ResourceType resType = SubcommandUtil.getResourceType(path, null);
      if ((resType != null) && (resType != ResourceType.FILE)) {
        throw StatusHelper.failure(NLS.bind(Messages.ChangesetExtractCmd_COMPLAIN_NOT_A_FILE, path.toOSString()), null);
      }
      
    }
    else if (SubcommandUtil.exists(path, null)) {
      throw StatusHelper.failure(NLS.bind(Messages.ChangesetExtractCmd_COMPLAIN_OVERWRITE, path.toOSString(), 
        cli.getDefinition().getOption(ChangesetExtractCmdOpts.OPT_OVERWRITE).getName()), null);
    }
  }
  
  private ChangeSyncDTO getChange(ChangeSetSyncDTO csDTO, String itemUuid, IRelativeLocation itemPath, String itemSelector) throws FileSystemException
  {
    ChangeSyncDTO matchedChange = null;
    Iterator localIterator2; for (Iterator localIterator1 = csDTO.getChanges().iterator(); localIterator1.hasNext(); 
        localIterator2.hasNext())
    {
      ChangeFolderSyncDTO changeFolderSyncDTO = (ChangeFolderSyncDTO)localIterator1.next();
      localIterator2 = changeFolderSyncDTO.getChanges().iterator(); continue;ChangeSyncDTO changeSyncDTO = (ChangeSyncDTO)localIterator2.next();
      if (itemUuid != null) {
        if (itemUuid.equals(changeSyncDTO.getVersionableItemId())) {
          matchedChange = changeSyncDTO;
        }
      } else {
        IRelativeLocation relCSPath = new RelativeLocation(new Path(changeSyncDTO.getPathHint()).segments());
        
        if (itemPath.sameLocation(relCSPath, false)) {
          matchedChange = changeSyncDTO;
        }
      }
      
      if (matchedChange != null) {
        break;
      }
    }
    

    if (matchedChange == null) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.ChangesetExtractCmd_NOT_A_VALID_ITEM, itemSelector));
    }
    
    if (matchedChange.getVersionableItemType().equals("folder")) {
      throw StatusHelper.inappropriateArgument(NLS.bind(Messages.ChangesetExtractCmd_COMPLAIN_NOT_A_FILE, 
        itemSelector));
    }
    
    return matchedChange;
  }
  
  private void validateState(String csId, ChangeSyncDTO change, String stateId, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRichClientRestService.class);
    
    IScmRichClientRestService.ParmsGetIntermediateHistory parms = new IScmRichClientRestService.ParmsGetIntermediateHistory();
    changeSetItemId = csId;
    versionableItemId = change.getVersionableItemId();
    
    if (change.getVersionableItemType().equals("file")) {
      versionableItemNamespace = IFileItem.ITEM_TYPE.getNamespaceURI();
      versionableItemType = IFileItem.ITEM_TYPE.getName();
    } else if (change.getVersionableItemType().equals("folder")) {
      versionableItemNamespace = IFolder.ITEM_TYPE.getNamespaceURI();
      versionableItemType = IFolder.ITEM_TYPE.getName();
    } else if (change.getVersionableItemType().equals("symbolic_link")) {
      versionableItemNamespace = ISymbolicLink.ITEM_TYPE.getNamespaceURI();
      versionableItemType = ISymbolicLink.ITEM_TYPE.getName();
    }
    
    ScmIntermediateHistory result = null;
    try {
      result = scmService.getChangeSetHistory(parms);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ListStatesCmd_FAILURE, e, 
        new IndentingPrintStream(config.getContext().stderr()));
    }
    
    ScmIntermediateChangeNode matchedChangeNode = null;
    for (ScmIntermediateChangeNode changeNode : result.getHistory()) {
      if ((changeNode.getStateId() != null) && (changeNode.getStateId().equals(stateId)) && 
        (changeNode.getType() != ScmIntermediateChangeNode.TYPE_DELETE)) {
        matchedChangeNode = changeNode;
      }
    }
    
    if (matchedChangeNode == null) {
      throw StatusHelper.argSyntax(Messages.ChangesetExtractCmd_INVALID_STATE);
    }
  }
  
  private void extractContent(ILocation diskPath, String wsId, String compId, String itemId, String stateId, String itemType, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    PrintStream out = null;
    boolean downloaded = false;
    try
    {
      out = new PrintStream(diskPath.toOSString());
      
      if (itemType.equals("file")) {
        RepoUtil.httpDownloadFile(repo, wsId, compId, itemId, stateId, out, config);
        downloaded = true;
      } else if (itemType.equals("symbolic_link")) {
        com.ibm.team.scm.common.IVersionableHandle verHandle = RepoUtil.getVersionableHandle(repo, itemId, stateId, itemType, config);
        if (verHandle != null)
        {
          try {
            symbolicLink = (ISymbolicLink)SCMPlatform.getWorkspaceManager(repo).versionableManager()
              .fetchCompleteState((ISymbolicLinkHandle)verHandle, null);
          } catch (VersionablePermissionDeniedException localVersionablePermissionDeniedException) { ISymbolicLink symbolicLink;
            throw StatusHelper.permissionFailure(Messages.Common_VERSIONABLE_PERMISSSION_DENIED);
          } catch (TeamRepositoryException e) {
            throw StatusHelper.wrap(Messages.ChangesetExtractCmd_IO_ERROR, e, 
              new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
          }
          try {
            ISymbolicLink symbolicLink;
            out.write(symbolicLink.getTarget().getBytes(Charset.defaultCharset().name()));
            out.flush();
            downloaded = true;
          } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage(), e);
          }
        }
      } else {
        throw StatusHelper.failure(Messages.ChangesetExtractCmd_UNABLE_TO_EXTRACT, null);
      }
    } catch (IOException e) { e = e;
      throw StatusHelper.failure(Messages.ChangesetExtractCmd_IO_ERROR, e);
    } finally { localObject = finally;
      if (out != null) {
        out.close();
      }
      
      if (!downloaded)
      {
        File file = new File(diskPath.toOSString());
        if (file.exists()) {
          file.delete();
        }
      }
      throw localObject;
    }
    if (out != null) {
      out.close();
    }
    
    if (!downloaded)
    {
      File file = new File(diskPath.toOSString());
      if (file.exists()) {
        file.delete();
      }
    }
  }
  
  private void load(String sandboxPath, String filePath, String itemId, String stateId, String itemType, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsLoadFileRequest loadFile = new ParmsLoadFileRequest();
    versionableItemId = itemId;
    versionableItemStateId = stateId;
    versionableItemType = itemType;
    sandboxPath = sandboxPath;
    filePath = filePath;
    
    ParmsLoadFiles parms = new ParmsLoadFiles();
    repositoryUrl = repo.getRepositoryURI();
    toLoad = new ParmsLoadFileRequest[] { loadFile };
    try
    {
      client.postLoadFiles(parms, null);
    } catch (VersionablePermissionDeniedException localVersionablePermissionDeniedException) {
      throw StatusHelper.permissionFailure(Messages.Common_VERSIONABLE_PERMISSSION_DENIED);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.ChangesetExtractCmd_UNABLE_TO_EXTRACT, e, 
        new IndentingPrintStream(config.getContext().stderr()));
    }
  }
}
