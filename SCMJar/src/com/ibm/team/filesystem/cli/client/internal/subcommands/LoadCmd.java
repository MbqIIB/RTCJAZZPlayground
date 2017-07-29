package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ISandboxHistoryRegistry;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.ConnectionInfo;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil.ItemInfo;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.ResourceType;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBackupDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLoad;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLoadComponentVersionables;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLoadRule;
import com.ibm.team.filesystem.client.rest.parameters.ParmsLoadVersionable;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.StatusDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.CollisionDTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.InvalidLoadRequestDTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.LoadEvaluationDTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.LoadLocationDTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.LoadOverlapDTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.LoadResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IFolderHandle;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;














public class LoadCmd
{
  public LoadCmd() {}
  
  public void run(IScmClientConfiguration config, ILocation cfaRoot, IScmCommandLineArgument wsSelector, List<IScmCommandLineArgument> cSelectors, boolean getAll, boolean loadCompRoots, IRelativeLocation relativeLoadTarget, LoadCmdLauncher.LoadRuleConfig loadRuleConfig, boolean quiet, String alternativeName)
    throws FileSystemException
  {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    ITeamRepository repo = null;
    if (config.getSubcommandCommandLine().hasOption(CommonOptions.OPT_URI)) {
      ConnectionInfo connectionInfo = config.getConnectionInfo();
      repo = RepoUtil.login(config, client, connectionInfo);
    } else {
      repo = RepoUtil.login(config, client, config.getConnectionInfo(wsSelector.getRepositorySelector()));
    }
    





















    IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, false, repo, config);
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    
    if (cSelectors != null) {
      RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, cSelectors, repo, config);
    }
    
    if ((loadRuleConfig != null) && 
      (getAll)) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmd_GET_ALL_ARGUMENT_MAY_NOT_BE_SPECIFIED_WITH_LOAD_RULE, CommonOptions.OPT_ALL.getName()));
    }
    

    if ((relativeLoadTarget != null) && ((cSelectors == null) || (cSelectors.size() != 1))) {
      throw StatusHelper.argSyntax(Messages.LoadCmd_MULTIPLE_COMPONENTS_GIVEN_WITH_TARGET);
    }
    

    List<String> compSelectors = null;
    if (cSelectors != null) {
      compSelectors = RepoUtil.getSelectors(cSelectors);
    }
    
    ParmsLoad parms = null;
    if (loadRuleConfig == null) {
      parms = generateLoadParms(ws, compSelectors, getAll, loadCompRoots, cfaRoot, relativeLoadTarget, alternativeName, client, repo, config);
    } else {
      parms = generateLoadParms(ws, compSelectors, loadRuleConfig, cfaRoot, relativeLoadTarget, client, repo, config);
    }
    
    if ((componentVersionablesToLoad == null) && (versionablesToLoad == null) && (loadRule == null) && 
      (!quiet))
    {
      config.getContext().stdout().println(Messages.LoadCmd_NO_LOAD_REQUESTS);
      return;
    }
    
    evaluateLoad(parms, config.getSubcommandCommandLine().hasOption(LoadCmdOptions.OPT_FORCE), client, repo, config);
    
    LoadResultDTO result = null;
    try {
      result = client.postLoadCFA(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.LoadCmd_11, e, new IndentingPrintStream(config.getContext().stderr()), repositoryUrl);
    }
    

    config.getSandboxHistoryRegistry().addWorkspace(cfaRoot, repo.getId().getUuidValue(), workspaceItemId);
    
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
    
    if (!quiet) {
      config.getContext().stdout().println(Messages.LoadCmd_SUCCESSFULLY_COMPLETED);
    }
  }
  
  private ParmsLoad generateLoadParms(ParmsWorkspace ws, List<String> compSelectors, boolean getAll, boolean loadCompRoots, ILocation cfaRoot, IRelativeLocation relativeLoadTarget, String alternativeName, IFilesystemRestClient client, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    ParmsLoad parms = new ParmsLoad();
    deleteRemovedShares = Boolean.valueOf(true);
    
    preserveLocalChanges = Boolean.valueOf(config.getSubcommandCommandLine().hasOption(LoadCmdOptions.OPT_RESYNC));
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    
    List<ParmsLoadComponentVersionables> compVersionables = new ArrayList();
    List<ParmsLoadVersionable> versionables = new ArrayList();
    
    if ((compSelectors == null) || (compSelectors.size() == 0))
    {


      compSelectors = new ArrayList(1);
      compSelectors.add(Character.toString('/'));
    }
    

    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    
    IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
    




    for (String selector : compSelectors) {
      String[] path = StringUtil.splitEscapedPath(selector);
      
      if (path.length == 0) {
        if (alternativeName != null) {
          ICommandLine subargs = config.getSubcommandCommandLine();
          throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmdLauncher_TOO_MANY_COMPONENTS, subargs.getDefinition().getOption(LoadCmdOptions.OPT_ALTERNATIVE_NAME).getName()));
        }
        
        if (relativeLoadTarget != null) {
          throw StatusHelper.argSyntax(Messages.LoadCmd_REMOTE_PATH_DISALLOWED);
        }
        



        for (WorkspaceComponentDTO comp : wsDetails.getComponents()) {
          if ((alternativeName != null) || (loadCompRoots)) {
            versionables.add(generateVersionables(ws, comp, cfaRoot, relativeLoadTarget, 
              alternativeName, loadCompRoots, null));
          } else {
            compVersionables.add(generateComponentVersionables(ws, comp, cfaRoot, relativeLoadTarget));
          }
          
        }
        
      }
      else
      {
        WorkspaceComponentDTO comp = getComponent(wsDetails, path[0], config);
        
        VersionableDTO remoteItem = null;
        if (path.length > 1) {
          String[] componentPath = new String[path.length - 1];
          System.arraycopy(path, 1, componentPath, 0, componentPath.length);
          
          remoteItem = RepoUtil.getVersionableByPath(scmService, wsDetails.getItemId(), 
            comp.getItemId(), StringUtil.createPathString(componentPath), config);
        }
        
        if ((remoteItem != null) || (alternativeName != null) || (loadCompRoots)) {
          ParmsLoadVersionable ver = generateVersionables(ws, comp, cfaRoot, relativeLoadTarget, alternativeName, 
            loadCompRoots, remoteItem);
          versionables.add(ver);
        } else {
          ParmsLoadComponentVersionables compVer = generateComponentVersionables(ws, comp, cfaRoot, relativeLoadTarget);
          compVersionables.add(compVer);
        }
      }
    }
    
    if (compVersionables.size() > 0) {
      componentVersionablesToLoad = ((ParmsLoadComponentVersionables[])compVersionables.toArray(new ParmsLoadComponentVersionables[compVersionables.size()]));
    }
    
    if (versionables.size() > 0) {
      versionablesToLoad = ((ParmsLoadVersionable[])versionables.toArray(new ParmsLoadVersionable[versionables.size()]));
    }
    
    return parms;
  }
  
  private ParmsLoadComponentVersionables generateComponentVersionables(ParmsWorkspace ws, WorkspaceComponentDTO comp, ILocation cfaRoot, IRelativeLocation relativeLoadTarget)
  {
    ParmsLoadComponentVersionables parms = new ParmsLoadComponentVersionables();
    workspace = ws;
    componentItemId = comp.getItemId();
    sandboxPath = cfaRoot.toOSString();
    if (relativeLoadTarget != null) {
      relativeLoadPath = relativeLoadTarget.toPath().toOSString();
    }
    
    return parms;
  }
  

  private ParmsLoadVersionable generateVersionables(ParmsWorkspace ws, WorkspaceComponentDTO comp, ILocation cfaRoot, IRelativeLocation relativeLoadTarget, String alternativeName, boolean loadCompRoots, VersionableDTO remoteItem)
  {
    ParmsLoadVersionable parms = new ParmsLoadVersionable();
    workspace = ws;
    componentItemId = comp.getItemId();
    sandboxPath = cfaRoot.toOSString();
    if (relativeLoadTarget != null) {
      relativeLoadPath = relativeLoadTarget.toPath().toOSString();
    }
    
    if ((alternativeName != null) || ((loadCompRoots) && (remoteItem == null))) {
      alternativeName = (loadCompRoots ? comp.getName() : alternativeName);
    }
    
    if (remoteItem != null) {
      if ((remoteItem.getVersionable() instanceof IFolderHandle)) {
        versionableItemType = "folder";
      } else if ((remoteItem.getVersionable() instanceof IFileItemHandle)) {
        versionableItemType = "file";
      } else {
        versionableItemType = "symbolic_link";
      }
      
      versionableItemId = remoteItem.getVersionable().getItemId().getUuidValue();
    } else {
      versionableItemType = "folder";
      versionableItemId = comp.getRootFolder();
    }
    
    return parms;
  }
  
  private WorkspaceComponentDTO getComponent(WorkspaceDetailsDTO wsDetails, String compSelector, IScmClientConfiguration config) throws FileSystemException
  {
    WorkspaceComponentDTO matchedComp = null;
    IUuidAliasRegistry.IUuidAlias compAlias = RepoUtil.lookupUuidAndAlias(compSelector);
    
    List<SubcommandUtil.ItemInfo> compsMatched = new ArrayList();
    for (WorkspaceComponentDTO comp : wsDetails.getComponents()) {
      if (((compAlias != null) && (compAlias.getUuid().getUuidValue().equals(comp.getItemId()))) || 
        (comp.getName().equals(compSelector))) {
        matchedComp = comp;
        SubcommandUtil.ItemInfo itemInfo = new SubcommandUtil.ItemInfo(comp.getName(), comp.getItemId(), wsDetails.getRepositoryURL(), RepoUtil.ItemType.COMPONENT);
        compsMatched.add(itemInfo);
      }
    }
    
    if (compsMatched.size() > 1) {
      SubcommandUtil.displayAmbiguousSelectorException(compSelector, compsMatched, config);
      throw StatusHelper.ambiguousSelector(NLS.bind(Messages.Common_AMBIGUOUS_COMPONENT, compSelector));
    }
    
    if (matchedComp == null) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.Common_COMP_NOT_FOUND, compSelector));
    }
    
    return matchedComp;
  }
  
  private ParmsLoad generateLoadParms(ParmsWorkspace ws, List<String> compSelectors, LoadCmdLauncher.LoadRuleConfig loadRuleConfig, ILocation cfaRoot, IRelativeLocation relativeLoadTarget, IFilesystemRestClient client, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    assert (loadRuleConfig != null);
    
    ParmsLoad parms = new ParmsLoad();
    deleteRemovedShares = Boolean.valueOf(true);
    
    loadRule = new ParmsLoadRule();
    preserveLocalChanges = Boolean.valueOf(config.getSubcommandCommandLine().hasOption(LoadCmdOptions.OPT_RESYNC));
    sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler = new ParmsBackupDilemmaHandler();
    sandboxUpdateDilemmaHandler.backupDilemmaHandler.backupEnabled = true;
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    

    WorkspaceDetailsDTO wsDetails = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(ws), client, config).get(0);
    
    ParmsLoadRule parmsLoadRule = new ParmsLoadRule();
    workspace = ws;
    sandboxPath = cfaRoot.toOSString();
    WorkspaceComponentDTO comp;
    if ((loadRuleConfig instanceof LoadCmdLauncher.RemoteLoadRuleConfig)) {
      LoadCmdLauncher.RemoteLoadRuleConfig remoteRule = (LoadCmdLauncher.RemoteLoadRuleConfig)loadRuleConfig;
      
      IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
      
      String[] path = StringUtil.splitEscapedPath(remotePath);
      

      comp = null;
      try {
        comp = getComponent(wsDetails, path[0], config);
        

        String[] newPath = new String[path.length - 1];
        System.arraycopy(path, 1, newPath, 0, path.length - 1);
        path = newPath;
      }
      catch (Exception localException) {}
      

      if (comp == null) {
        if ((compSelectors == null) || (compSelectors.size() != 1)) {
          throw StatusHelper.argSyntax(Messages.LoadCmd_COMP_NOT_FOUND_IN_PATH);
        }
        


        comp = getComponent(wsDetails, (String)compSelectors.get(0), config);
      }
      

      String remotePath = StringUtil.createPathString(path);
      
      VersionableDTO verDTO = RepoUtil.getVersionableByPath(scmService, workspaceItemId, comp.getItemId(), remotePath, config);
      if (!(verDTO.getVersionable() instanceof IFileItemHandle)) {
        throw StatusHelper.misconfiguredRemoteFS(NLS.bind(Messages.LoadCmd_28, remotePath));
      }
      
      componentItemId = comp.getItemId();
      loadRuleFileItemId = verDTO.getVersionable().getItemId().getUuidValue();
      
      if (compSelectors != null) {
        componentItemIdFilter = new String[compSelectors.size()];
        int count = 0;
        for (String compSelector : compSelectors) {
          WorkspaceComponentDTO component = getComponent(wsDetails, compSelector, config);
          componentItemIdFilter[count] = component.getItemId();
        }
      }
    }
    else {
      assert ((loadRuleConfig instanceof LoadCmdLauncher.LocalLoadRuleConfig));
      
      LoadCmdLauncher.LocalLoadRuleConfig localRule = (LoadCmdLauncher.LocalLoadRuleConfig)loadRuleConfig;
      
      loadRuleFileContents = getFileContent(localPath, config);
      
      if (compSelectors != null) {
        componentItemIdFilter = new String[compSelectors.size()];
        int count = 0;
        for (String compSelector : compSelectors) {
          WorkspaceComponentDTO component = getComponent(wsDetails, compSelector, config);
          componentItemIdFilter[count] = component.getItemId();
        }
      }
    }
    
    loadRule = parmsLoadRule;
    return parms;
  }
  
  private String getFileContent(String path, IScmClientConfiguration config)
    throws FileSystemException
  {
    String filename;
    String filename;
    if ("-".equals(path)) {
      InputStream in = config.getContext().stdin();
      filename = Messages.LoadCmd_30;
    }
    else {
      try {
        filename = SubcommandUtil.canonicalize(path);
      } catch (FileSystemException localFileSystemException) { String filename;
        filename = path;
      }
      IPath localFile = new Path(filename);
      
      ResourceType resourceType = SubcommandUtil.getResourceType(localFile, null);
      if (resourceType == null)
        throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmd_31, filename));
      if (resourceType != ResourceType.FILE) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmd_32, filename));
      }
      try
      {
        in = new FileInputStream(localFile.toFile());
      } catch (FileNotFoundException e) { InputStream in;
        throw StatusHelper.misconfiguredLocalFS(NLS.bind(Messages.LoadCmd_33, filename, e.getMessage()));
      }
    }
    InputStream in;
    StringBuilder out = new StringBuilder();
    char[] buffer = new char[65536];
    try {
      InputStreamReader inReader = new InputStreamReader(in);
      int read;
      while ((read = inReader.read(buffer, 0, buffer.length)) != -1) { int read;
        out.append(buffer, 0, read);
      }
    } catch (Exception e) {
      throw StatusHelper.misconfiguredRemoteFS(NLS.bind(Messages.LoadCmd_34, filename, e.getMessage()));
    } finally {
      try {
        if (!filename.equals(Messages.LoadCmd_30)) {
          in.close();
        }
      }
      catch (IOException localIOException1) {}
    }
    
    return out.toString();
  }
  



  private void evaluateLoad(ParmsLoad parms, boolean overwrite, IFilesystemRestClient client, ITeamRepository repo, IScmClientConfiguration config)
    throws FileSystemException
  {
    LoadEvaluationDTO result = null;
    try
    {
      result = client.postValidateLoadCFA(parms, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.LoadCmd_11, e, new IndentingPrintStream(config.getContext().stderr()), 
        repo.getRepositoryURI());
    }
    
    IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
    
    if (result.isInvalidFilterForOldLoadRuleFormat()) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.LoadCmd_INVALID_OLD_LOAD_RULE_FILTER, 
        ((OptionKey)LoadCmdOptions.OPT_REMOTE_PATHS).getName()));
    }
    
    if (result.getLoadRuleProblems().size() > 0) {
      processLoadRuleProblems(result.getLoadRuleProblems(), err, client, config);
    }
    
    boolean doNotReportOutOfSyncOrCollisions = (overwrite) || (preserveLocalChanges.booleanValue());
    if (result.getCollisions().size() > 0) {
      processCollisions(result.getCollisions(), doNotReportOutOfSyncOrCollisions, err, client, config);
    }
    
    if (result.getInvalidLoadLocations().size() > 0) {
      processInvalidLoadRequests(result.getInvalidLoadLocations(), err, client, config);
    }
    
    if (result.getInvalidLoadRequests().size() > 0) {
      processInvalidLoadRequests(result.getInvalidLoadRequests(), err, client, config);
    }
    
    if (result.getOverlappingLoadRequests().size() > 0) {
      processOverlappingLoadRequests(result.getOverlappingLoadRequests(), err, client, config);
    }
    
    if ((!doNotReportOutOfSyncOrCollisions) && (result.getSharesOutOfSync().size() > 0)) {
      AcceptResultDisplayer.showOutOfSync(result.getSharesOutOfSync(), config);
    }
  }
  
  private void processLoadRuleProblems(List<StatusDTO> loadRuleProblems, IndentingPrintStream err, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    Iterator localIterator = loadRuleProblems.iterator(); if (localIterator.hasNext()) { StatusDTO status = (StatusDTO)localIterator.next();
      throw StatusHelper.createException(status.getMessage(), 44, null);
    }
  }
  
  private void processCollisions(List<CollisionDTO> collisions, boolean overwrite, IndentingPrintStream err, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    boolean collisionWithExistingShare = false;
    boolean collisionWithExistingItem = false;
    
    for (CollisionDTO collision : collisions)
    {
      if ((!overwrite) || (collision.getLoadLocations().size() > 1)) {
        err.println(collision.getDetail());
        
        List<String> compList = new ArrayList();
        for (LoadLocationDTO location : collision.getLoadLocations()) {
          if (!compList.contains(location.getComponentItemId())) {
            printComponent(location, err.indent(), client, config);
            compList.add(location.getComponentItemId());
          }
        }
        
        collisionWithExistingShare = true;
      }
      

      if ((collision.isCollidedWithExistingContent()) && (!overwrite)) {
        PathDTO path = collision.getPath();
        config.getContext().stderr().println(
          NLS.bind(Messages.LoadCmd_16, StringUtil.createPathString(path.getSegments())));
        
        collisionWithExistingItem = true;
      }
    }
    
    if (collisionWithExistingShare) {
      StringBuffer msg = new StringBuffer(Messages.LoadCmd_7).append(
        overwrite ? Messages.LoadCmd_8 : NLS.bind(Messages.LoadCmd_8_1, LoadCmdOptions.OPT_FORCE.getName()));
      throw StatusHelper.collision(msg.toString()); }
    if (collisionWithExistingItem) {
      StringBuffer msg = new StringBuffer(Messages.LoadCmd_7).append(NLS.bind(Messages.LoadCmd_9, LoadCmdOptions.OPT_FORCE.getName()));
      throw StatusHelper.collision(msg.toString());
    }
  }
  
  private void printComponent(LoadLocationDTO location, IndentingPrintStream err, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    WorkspaceDetailsDTO wsDetails = 
    
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(new ParmsWorkspace(location.getRepositoryURL(), location.getContextItemId())), client, config).get(0);
    
    WorkspaceComponentDTO compDTO = getComponent(wsDetails, location.getComponentItemId(), config);
    
    err.println(NLS.bind(Messages.LoadCmd_24, AliasUtil.selector(compDTO.getName(), 
      UUID.valueOf(compDTO.getItemId()), location.getRepositoryURL(), RepoUtil.ItemType.COMPONENT)));
  }
  
  private void processInvalidLoadRequests(List<InvalidLoadRequestDTO> invalidLoadRequests, IndentingPrintStream err, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    err.println(Messages.LoadCmd_INVALID_LOAD_REQUEST);
    for (InvalidLoadRequestDTO invalidLoadRequest : invalidLoadRequests) {
      if (invalidLoadRequest.isSetReason()) {
        config.getContext().stderr().println(invalidLoadRequest.getReason());
      }
      
      LoadLocationDTO location = invalidLoadRequest.getLoadLocation();
      printComponent(location, err.indent(), client, config);
    }
    
    throw StatusHelper.collision(Messages.LoadCmd_7 + 
      Messages.LoadCmd_OVERLAPPING_SHARES_SUMMARY);
  }
  
  private void processOverlappingLoadRequests(List<LoadOverlapDTO> overlappingLoadRequests, IndentingPrintStream err, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    List<String> overlappedShares = new ArrayList();
    List<String> compList = new ArrayList();
    
    for (LoadOverlapDTO overlapLoadRequest : overlappingLoadRequests) {
      if (overlapLoadRequest.getOverlappingShares().size() > 0) {
        if (overlapLoadRequest.getOverlappingShares().size() == 0) {
          err.println(Messages.LoadCmd_OVERLAPPING_SHARE_COMPLAINT);
        }
        
        for (ShareDTO share : overlapLoadRequest.getOverlappingShares()) {
          String overlappedShare = StringUtil.createPathString(share.getPath().getSegments());
          if (!overlappedShares.contains(overlappedShare)) {
            overlappedShares.add(overlappedShare);
            err.indent().println(overlappedShare);
          }
        }
      }
      

      if (overlappedShares.size() <= 0)
      {


        if (overlapLoadRequest.getOverlappingItems().size() > 0) {
          if (compList.size() == 0) {
            err.println(Messages.LoadCmd_OVERLAPPING_ITEM_COMPLAINT);
          }
          
          LoadLocationDTO location = overlapLoadRequest.getLoadLocation();
          if (!compList.contains(location.getComponentItemId())) {
            printComponent(location, err.indent(), client, config);
            compList.add(location.getComponentItemId());
          }
        }
      }
    }
    


    StringBuffer msg = new StringBuffer(Messages.LoadCmd_7).append(
      overlappedShares.size() > 0 ? Messages.LoadCmd_OVERLAPPING_SHARES_SUMMARY : Messages.LoadCmd_OVERLAPPING_ITEMS_SUMMARY);
    throw StatusHelper.collision(msg.toString());
  }
}
