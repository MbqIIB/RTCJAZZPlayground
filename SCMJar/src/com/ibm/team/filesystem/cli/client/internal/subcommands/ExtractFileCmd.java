package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.BaselineDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLineArgument;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IBaselineHandle;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.dto.IVersionableIdentifier;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionablePath;
import com.ibm.team.scm.common.internal.util.VersionableIdentifierUtil;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.osgi.util.NLS;


public class ExtractFileCmd
  extends AbstractSubcommand
{
  public ExtractFileCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAnc(config, client);
    
    UUID itemUuid = null;
    UUID itemState = null;
    if ((cli.hasOption(ExtractFileCmdOptions.OPT_WORKSPACE)) || 
      (cli.hasOption(ExtractFileCmdOptions.OPT_SNAPSHOT)) || 
      (cli.hasOption(ExtractFileCmdOptions.OPT_BASELINE))) {
      if (!cli.hasOption(ExtractFileCmdOptions.OPT_COMPONENT)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ExtractFileCmd_MissingOption, ExtractFileCmdOptions.OPT_COMPONENT.getShortOpt()));
      }
      if (!cli.hasOption(ExtractFileCmdOptions.OPT_FILEPATH)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ExtractFileCmd_MissingOption, ExtractFileCmdOptions.OPT_FILEPATH.getShortOpt()));
      }
      

      IScmCommandLineArgument selector = ScmCommandLineArgument.create(cli.getOptionValue(ExtractFileCmdOptions.OPT_VER_ITEM.getId()), config);
      repo = RepoUtil.loginUrlArgAncestor(config, client, selector);
      

      IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ExtractFileCmdOptions.OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
      RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, Collections.singletonList(compSelector), repo, config);
      IComponent comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
      

      String filePath = cli.getOptionValue(ExtractFileCmdOptions.OPT_FILEPATH).getValue();
      String[] path = StringUtil.splitEscapedPath(filePath);
      filePath = path.length == 0 ? Character.toString('/') : toPath(path, false);
      

      ParmsWorkspace ws = null;
      String itemId = null;
      IItemType itemType = null;
      boolean foundCompInContext = false;
      
      if (cli.hasOption(ExtractFileCmdOptions.OPT_WORKSPACE)) {
        SubcommandUtil.validateArgument(selector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
        IWorkspace wsFound = RepoUtil.getWorkspace(selector.getItemSelector(), true, true, repo, config);
        ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
        
        itemId = workspaceItemId;
        itemType = IWorkspace.ITEM_TYPE;
        

        WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
        
        for (WorkspaceComponentDTO compDTO : wsDetails.getComponents()) {
          if (comp.getItemId().getUuidValue().equals(compDTO.getItemId())) {
            foundCompInContext = true;
            break;
          }
        }
      }
      else if (cli.hasOption(ExtractFileCmdOptions.OPT_SNAPSHOT)) {
        SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.SNAPSHOT);
        IBaselineSet snapshot = RepoUtil.getSnapshot(null, selector.getItemSelector(), repo, config);
        itemId = snapshot.getItemId().getUuidValue();
        itemType = IBaselineSet.ITEM_TYPE;
        

        List<IBaselineHandle> blHandles = snapshot.getBaselines();
        List<String> blIds = new ArrayList();
        
        for (IBaselineHandle blHandle : blHandles) {
          blIds.add(blHandle.getItemId().getUuidValue());
        }
        
        Object baselineDTOList = RepoUtil.getBaselinesById(blIds, repo.getRepositoryURI(), client, config);
        for (BaselineDTO blDTO : (List)baselineDTOList) {
          if (blDTO.getComponentItemId().equals(comp.getItemId().getUuidValue())) {
            foundCompInContext = true;
            break;
          }
        }
      }
      else if (cli.hasOption(ExtractFileCmdOptions.OPT_BASELINE)) {
        SubcommandUtil.validateArgument(selector, RepoUtil.ItemType.BASELINE);
        IBaseline bl = RepoUtil.getBaseline(selector.getItemSelector(), comp.getItemId().getUuidValue(), comp.getName(), repo, client, config);
        itemId = bl.getItemId().getUuidValue();
        itemType = IBaseline.ITEM_TYPE;
        foundCompInContext = true;
      }
      
      if (!foundCompInContext) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.Common_COMP_NOT_FOUND, compSelector.getItemSelector()));
      }
      

      IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
        .getServiceInterface(IScmRichClientRestService.class);
      ScmVersionablePath scmPath = RepoUtil.getVersionable2(scmService, itemId, itemType, comp.getItemId().getUuidValue(), 
        filePath, config);
      if ((scmPath == null) || (scmPath.getVersionable() == null)) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ExtractFileCmd_PATH_NOT_FOUND, filePath));
      }
      IVersionableHandle vh = scmPath.getVersionable();
      itemUuid = vh.getItemId();
      itemState = vh.getStateId();
    }
    else {
      if (cli.hasOption(ExtractFileCmdOptions.OPT_COMPONENT)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ExtractFileCmd_MissingOption2, new Object[] {
          ExtractFileCmdOptions.OPT_COMPONENT.getShortOpt(), 
          ExtractFileCmdOptions.OPT_BASELINE.getShortOpt(), 
          ExtractFileCmdOptions.OPT_WORKSPACE.getShortOpt(), 
          ExtractFileCmdOptions.OPT_SNAPSHOT.getShortOpt() }));
      }
      
      if (cli.hasOption(ExtractFileCmdOptions.OPT_FILEPATH)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ExtractFileCmd_MissingOption2, new Object[] {
          ExtractFileCmdOptions.OPT_FILEPATH.getShortOpt(), 
          ExtractFileCmdOptions.OPT_BASELINE.getShortOpt(), 
          ExtractFileCmdOptions.OPT_WORKSPACE.getShortOpt(), 
          ExtractFileCmdOptions.OPT_SNAPSHOT.getShortOpt() }));
      }
      

      IScmCommandLineArgument itemSelector = ScmCommandLineArgument.create(cli.getOptionValue(ExtractFileCmdOptions.OPT_VER_ITEM.getId()), config);
      try
      {
        itemUuid = UUID.valueOf(itemSelector.getItemSelector());
      } catch (IllegalArgumentException e) {
        throw StatusHelper.argSyntax(e.getMessage());
      }
    }
    
    IScmCommandLineArgument stateSelector = null;
    String diskPathStr = null;
    
    String stateStr = null;
    if (cli.hasOption(ExtractFileCmdOptions.OPT_VER_STATE.getId())) {
      stateStr = cli.getOption(ExtractFileCmdOptions.OPT_VER_STATE.getId());
    }
    
    if (cli.hasOption(ExtractFileCmdOptions.OPT_DISKPATH.getId())) {
      diskPathStr = cli.getOption(ExtractFileCmdOptions.OPT_DISKPATH.getId());
    }
    
    if ((stateStr == null) && (diskPathStr == null)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ExtractFileCmd_0, ExtractFileCmdOptions.OPT_VER_STATE.toString(), ExtractFileCmdOptions.OPT_DISKPATH.toString()));
    }
    if ((stateStr != null) && (diskPathStr == null)) {
      diskPathStr = stateStr;
      stateStr = null;
    }
    else {
      stateSelector = ScmCommandLineArgument.create(cli.getOptionValue(ExtractFileCmdOptions.OPT_VER_STATE.getId()), config);
    }
    


    UUID requestedState = null;
    if (stateSelector != null) {
      String stateArg = stateSelector.getItemSelector();
      
      try
      {
        requestedState = UUID.valueOf(stateArg);
      }
      catch (IllegalArgumentException localIllegalArgumentException1)
      {
        try {
          version = VersionableIdentifierUtil.toVersionIdentifier(stateArg);
        } catch (IllegalArgumentException localIllegalArgumentException2) { IVersionableIdentifier version;
          throw StatusHelper.argSyntax(NLS.bind(Messages.ExtractFileCmd_1, stateArg));
        }
        IVersionableIdentifier version;
        IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
          .getServiceInterface(IScmRichClientRestService.class);
        UUID repositoryId = VersionableIdentifierUtil.getRepositoryId(version);
        String repoIdStr = repositoryId == null ? null : repositoryId.getUuidValue();
        UUID result = RepoUtil.getStateId(scmService, itemUuid, 
          version.getShortVersionId(), repoIdStr, config);
        requestedState = result == null ? null : result;
        if (requestedState == null) {
          throw StatusHelper.itemNotFound(NLS.bind(
            Messages.ExtractFileCmd_VERSION_NUMBER_NOT_FOUND, 
            stateArg));
        }
      }
      
      if (itemState == null) {
        itemState = requestedState;
      }
      
      if (requestedState != null)
      {
        itemState = requestedState;
      }
    }
    

    ILocation diskPath = SubcommandUtil.makeAbsolutePath(config, diskPathStr);
    validateDiskPath(diskPath, client, config);
    
    if (itemState == null) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.ExtractFileCmd_3, 
        new Object[] { ExtractFileCmdOptions.OPT_WORKSPACE.toString(), ExtractFileCmdOptions.OPT_BASELINE.toString(), ExtractFileCmdOptions.OPT_SNAPSHOT.toString() }));
    }
    


    extractContent(diskPath, itemUuid.getUuidValue(), itemState, repo, config);
    config.getContext().stdout().println(NLS.bind(Messages.ChangesetExtractCmd_SUCCESS, diskPath.toOSString()));
  }
  
  private void validateDiskPath(ILocation diskPath, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    if (RepoUtil.isSandboxPath(diskPath.toOSString(), client, config)) {
      throw StatusHelper.failure(Messages.ExtractFileCmd_PATH_SANDBOX_DESCENDANT, null);
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
    
    if (cli.hasOption(ExtractFileCmdOptions.OPT_OVERWRITE)) {
      ResourceType resType = SubcommandUtil.getResourceType(path, null);
      if ((resType != null) && (resType != ResourceType.FILE)) {
        throw StatusHelper.failure(NLS.bind(Messages.ChangesetExtractCmd_COMPLAIN_NOT_A_FILE, path.toOSString()), null);
      }
      
    }
    else if (SubcommandUtil.exists(path, null)) {
      throw StatusHelper.failure(NLS.bind(Messages.ChangesetExtractCmd_COMPLAIN_OVERWRITE, path.toOSString(), 
        cli.getDefinition().getOption(ExtractFileCmdOptions.OPT_OVERWRITE).getName()), null);
    }
  }
  
  private void extractContent(ILocation diskPath, String itemId, UUID stateId, ITeamRepository repo, IScmClientConfiguration config) throws FileSystemException
  {
    PrintStream out = null;
    boolean downloaded = false;
    File file;
    try { out = new PrintStream(diskPath.toOSString());
      RepoUtil.httpDownloadFile(repo, null, null, itemId, stateId.getUuidValue(), out, config);
      downloaded = true;
    } catch (IOException e) {
      throw StatusHelper.failure(Messages.ChangesetExtractCmd_IO_ERROR, e);
    } finally {
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
  }
  
  private String toPath(String[] path, boolean isFolder) {
    return StringUtil.createPathString(path) + (isFolder ? Character.valueOf('/') : "");
  }
}
