package com.ibm.team.filesystem.cli.client.internal.sharecommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsShare;
import com.ibm.team.filesystem.client.rest.parameters.ParmsShareRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.CommitDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.EncodingErrorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.LineDelimiterErrorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SymlinkWarningDTO;
import com.ibm.team.filesystem.common.internal.rest.client.share.ShareOverlapDTO;
import com.ibm.team.filesystem.common.internal.rest.client.share.ShareResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IItemType;
import com.ibm.team.repository.common.LogFactory;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IFolder;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionablePath;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmVersionablePathList;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsGetPaths;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.logging.Log;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.osgi.util.NLS;


public class ShareCmd
  extends AbstractSubcommand
{
  private static final Log log = LogFactory.getLog(ShareCmd.class.getName());
  
  public ShareCmd() {}
  
  public void run() throws FileSystemException { ResourcesPlugin.getWorkspace();
    
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShareCmdOpts.OPT_WORKSPACE_SELECTOR), config);
    SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
    IScmCommandLineArgument compSelector = ScmCommandLineArgument.create(cli.getOptionValue(ShareCmdOpts.OPT_COMPONENT_SELECTOR), config);
    SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    

    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    
    IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, false, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    
    RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, Collections.singletonList(compSelector), repo, config);
    

    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    

    WorkspaceComponentDTO component = null;
    IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector.getItemSelector());
    for (WorkspaceComponentDTO comp : wsDetails.getComponents()) {
      if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(comp.getItemId()))) || 
        (compSelector.getItemSelector().equals(comp.getName()))) {
        component = comp;
      }
    }
    
    if (component == null) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.Common_COMP_NOT_FOUND, compSelector.getItemSelector()));
    }
    
    String remotePathUUID = null;
    List pathsList; if (cli.hasOption(ShareCmdOpts.OPT_REMOTE_PATH)) {
      String originalRemotePathInput = cli.getOption(ShareCmdOpts.OPT_REMOTE_PATH);
      ScmVersionablePath scmPath = null;
      
      IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
        .getServiceInterface(IScmRichClientRestService.class);
      

      if (RepoUtil.isUuid(originalRemotePathInput)) {
        IScmRichClientRestService.ParmsGetPaths parms = null;
        parms = new IScmRichClientRestService.ParmsGetPaths();
        componentItemId = component.getItemId();
        contextItemId = wsDetails.getItemId();
        contextItemType = IWorkspace.ITEM_TYPE.getName();
        contextItemNamespace = IWorkspace.ITEM_TYPE.getNamespaceURI();
        versionableItemIds = new String[] { originalRemotePathInput };
        versionableItemTypes = new String[] { IFolder.ITEM_TYPE.getName() };
        versionableItemNamespaces = new String[] { IFolder.ITEM_TYPE.getNamespaceURI() };
        try
        {
          ScmVersionablePathList vPathList = scmService.getVersionablePaths(parms);
          if (vPathList == null) break label644;
          pathsList = vPathList.getPaths();
          
          if (pathsList == null) break label644;
          int pathSize = pathsList.size();
          if (pathSize == 1) {
            scmPath = (ScmVersionablePath)pathsList.get(0);
            break label644;
          }
          if (pathSize <= 1) break label644;
          throw StatusHelper.itemNotFound(NLS.bind(Messages.ShareCmd_18, originalRemotePathInput));



        }
        catch (TeamRepositoryException localTeamRepositoryException1) {}



      }
      else
      {


        String[] path = StringUtil.splitEscapedPath(originalRemotePathInput);
        String remotePathInput = 
          StringUtil.createPathString(path) + '/';
        
        scmPath = RepoUtil.getVersionable2(scmService, wsDetails.getItemId(), IWorkspace.ITEM_TYPE, 
          component.getItemId(), remotePathInput, config);
      }
      label644:
      if ((scmPath == null) || (scmPath.getVersionable() == null)) {
        throw StatusHelper.itemNotFound(NLS.bind(Messages.ShareCmd_17, originalRemotePathInput));
      }
      remotePathUUID = scmPath.getVersionable().getItemId().getUuidValue();
    }
    
    ParmsShare parmsShare = new ParmsShare();
    shareRequests = generateShareRequests(cli.getOptions(ShareCmdOpts.OPT_TO_SHARE), ws, component, remotePathUUID, config);
    overwrite = Boolean.valueOf(cli.hasOption(ShareCmdOpts.OPT_IGNORE_EXISTING_SHARE));
    
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    try
    {
      shareResult = client.postShare(parmsShare, null);
    } catch (TeamRepositoryException e) { ShareResultDTO shareResult;
      throw StatusHelper.wrap(Messages.ShareCmd_10, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    ShareResultDTO shareResult;
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    if ((cli.hasOption(CommonOptions.OPT_VERBOSE)) && 
      (shareResult.getChangeSetsCommitted().size() > 0)) {
      printChangeSets(ws, component.getItemId(), shareResult.getChangeSetsCommitted(), client, config, out);
    }
    


    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    Iterator localIterator2; if (shareResult.getShareOverlaps().size() > 0) {
      err.println(Messages.ShareCmd_OVERLAPPING_SHARES);
      for (pathsList = shareResult.getShareOverlaps().iterator(); pathsList.hasNext(); 
          localIterator2.hasNext())
      {
        ShareOverlapDTO shareOverlap = (ShareOverlapDTO)pathsList.next();
        localIterator2 = shareOverlap.getOverlappingShares().iterator(); continue;ShareDTO shareDTO = (ShareDTO)localIterator2.next();
        List<String> pathSegments = shareDTO.getPath().getSegments();
        String path = StringUtil.createPathString((String[])pathSegments.toArray(new String[pathSegments.size()]));
        
        err.indent().println(path);
      }
      

      throw StatusHelper.disallowed(Messages.ShareCmd_OVERLAPPING_SHARES_FAILURE);
    }
    
    if (shareResult.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
      SubcommandUtil.showDeletedContent(shareResult.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
    }
    
    String NEWLINE = System.getProperty("line.separator");
    

    if (shareResult.getCommitDilemma().getEncodingFailures().size() > 0) {
      StringBuffer encFailureMessage = new StringBuffer();
      encFailureMessage.append(Messages.ShareCmd_ENCODING_FAILURES);
      for (EncodingErrorDTO encErr : shareResult.getCommitDilemma().getEncodingFailures()) {
        List<String> pathSegments = encErr.getShareable().getRelativePath().getSegments();
        String path = StringUtil.createPathString((String[])pathSegments.toArray(new String[pathSegments.size()]));
        
        encFailureMessage.append(NEWLINE);
        encFailureMessage.append(path);
      }
      
      log.warn(encFailureMessage.toString());
      
      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        out.print(encFailureMessage);
      }
    }
    

    if (shareResult.getCommitDilemma().getLineDelimiterFailures().size() > 0) {
      StringBuffer ldFailureMessage = new StringBuffer();
      ldFailureMessage.append(Messages.ShareCmd_LINE_DELIM_FAILURES);
      for (LineDelimiterErrorDTO ldErr : shareResult.getCommitDilemma().getLineDelimiterFailures()) {
        List<String> pathSegments = ldErr.getFileName().getSegments();
        String path = StringUtil.createPathString((String[])pathSegments.toArray(new String[pathSegments.size()]));
        
        ldFailureMessage.append(NEWLINE);
        ldFailureMessage.append(path);
      }
      
      log.warn(ldFailureMessage.toString());
      
      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        out.print(ldFailureMessage);
      }
    }
    

    if (shareResult.getCommitDilemma().getNonInteroperableLinks().size() > 0) {
      StringBuffer msg = new StringBuffer();
      msg.append(Messages.CheckInCmd_2);
      msg.append(NEWLINE);
      for (Object o : shareResult.getCommitDilemma().getNonInteroperableLinks()) {
        SymlinkWarningDTO warning = (SymlinkWarningDTO)o;
        
        msg.append(NLS.bind(Messages.CheckInCmd_4, new Object[] {
          warning.getLocation(), 
          warning.getTarget(), 
          warning.getType() }));
        
        msg.append(NEWLINE);
      }
      
      log.warn(msg);
      
      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        out.print(msg);
      }
    }
    

    if (shareResult.getCommitDilemma().getBrokenLinks().size() > 0) {
      StringBuffer msg = new StringBuffer();
      msg.append(Messages.CheckInCmd_5);
      for (Object o : shareResult.getCommitDilemma().getBrokenLinks()) {
        SymlinkWarningDTO warning = (SymlinkWarningDTO)o;
        
        msg.append(NLS.bind(Messages.CheckInCmd_4, new Object[] {
          warning.getLocation(), 
          warning.getTarget(), 
          warning.getType() }));
        
        msg.append(NEWLINE);
      }
      
      log.warn(msg);
      
      if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
        out.print(msg);
      }
    }
    
    out.println(Messages.ShareCmd_6);
  }
  
  private ParmsShareRequest[] generateShareRequests(List<String> projectRoots, ParmsWorkspace ws, WorkspaceComponentDTO comp, String remotePathUUID, IScmClientConfiguration config) throws FileSystemException
  {
    List<ParmsShareRequest> shareRequests = new ArrayList();
    Map<String, List<String>> sandboxToShares = new HashMap();
    
    List<ILocation> roots = SubcommandUtil.makeAbsolutePaths(config, projectRoots);
    for (ILocation root : roots) {
      if (!SubcommandUtil.exists(root, null)) {
        throw StatusHelper.misconfiguredLocalFS(NLS.bind(Messages.ShareCmd_2, root));
      }
      
      ILocation rootParent = root.getParent();
      File sandboxFile = null;
      if (!rootParent.isEmpty()) {
        sandboxFile = SubcommandUtil.findAncestorCFARoot(root.toOSString());
      }
      ILocation sandboxPath;
      ILocation sandboxPath;
      if (sandboxFile == null) {
        sandboxPath = rootParent;
      } else {
        sandboxPath = new PathLocation(sandboxFile.getAbsolutePath());
      }
      
      if (!rootParent.equals(sandboxPath)) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.ShareCmd_3, rootParent.toOSString(), sandboxPath.toOSString()));
      }
      
      List<String> shares = (List)sandboxToShares.get(sandboxPath.toOSString());
      if (shares == null) {
        shares = new ArrayList();
        sandboxToShares.put(sandboxPath.toOSString(), shares);
      }
      
      shares.add(root.getLocationRelativeTo(sandboxPath).toString());
    }
    
    for (Map.Entry<String, List<String>> entry : sandboxToShares.entrySet()) {
      ParmsShareRequest shareRequest = new ParmsShareRequest();
      workspace = ws;
      componentItemId = comp.getItemId();
      parentFolderItemId = (remotePathUUID == null ? comp.getRootFolder() : remotePathUUID);
      sandboxPath = ((String)entry.getKey());
      itemsToShare = ((String[])((List)entry.getValue()).toArray(new String[((List)entry.getValue()).size()]));
      
      shareRequests.add(shareRequest);
    }
    
    return (ParmsShareRequest[])shareRequests.toArray(new ParmsShareRequest[shareRequests.size()]);
  }
  
  private void printChangeSets(ParmsWorkspace ws, String compId, List<String> csList, IFilesystemRestClient client, IScmClientConfiguration config, IndentingPrintStream out) throws FileSystemException
  {
    config.getSubcommandCommandLine();
    
    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.setVerbose(true);
    options.enablePrinter(4);
    options.enableFilter(4);
    
    List<ChangeSetSyncDTO> changeSetList = new ArrayList(csList.size());
    for (String cs : csList) {
      ChangeSetSyncDTO changeSet = RepoUtil.findChangeSet(cs, false, workspaceItemId, 
        "workspace", repositoryUrl, client, config);
      changeSetList.add(changeSet);
      
      options.addFilter(UUID.valueOf(changeSet.getChangeSetItemId()), 4);
    }
    
    PendingChangesUtil.printChangeSets(null, changeSetList, new ChangeSetStateFactory(), options, out, client, config);
  }
}
