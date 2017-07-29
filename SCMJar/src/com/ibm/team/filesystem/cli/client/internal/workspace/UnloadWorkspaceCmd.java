package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ISandboxHistoryRegistry;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.ISandboxWorkspace;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.IRelativeLocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPath;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPendingChangesDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsUnload;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceComponent;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConfigurationWithUncheckedInChanges2DTO;
import com.ibm.team.filesystem.common.internal.rest.client.load.UnLoadResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IWorkspace;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;



public class UnloadWorkspaceCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public UnloadWorkspaceCmd() {}
  
  public static final IPositionalOptionDefinition OPT_SELECTORS = new PositionalOptionDefinition(
    "selectors", 0, -1, "@");
  public static final INamedOptionDefinition OPT_WORKSPACE = new NamedOptionDefinition(
    "w", "workspace", 1, "@");
  public static final INamedOptionDefinition OPT_COMPONENTS = new NamedOptionDefinition(
    "C", "components", 0);
  public static final INamedOptionDefinition OPT_PATHS = new NamedOptionDefinition(
    "p", "paths", 0);
  
  public static final NamedOptionDefinition OPT_OVERWRITE_UNCOMMITTED_DEPRECATED = new NamedOptionDefinition("o", "overwrite-uncommitted", 0);
  public static final IOptionKey OPT_DELETE = new OptionKey("delete");
  

  public static final NamedOptionDefinition OPT_IGNORE_UNCOMMITTED = new NamedOptionDefinition("i", "ignore-uncommitted", 0);
  

  private String unloadedWorkspace = null;
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    OPT_OVERWRITE_UNCOMMITTED_DEPRECATED.hideOption();
    
    options.addOption(CommonOptions.OPT_DIRECTORY, CommonOptions.OPT_DIRECTORY_HELP)
      .addOption(CommonOptions.OPT_NO_LOCAL_REFRESH, Messages.Common_DO_NOT_SCAN_FS)
      .addOption(OPT_OVERWRITE_UNCOMMITTED_DEPRECATED, Messages.UnloadWorkspaceCmdOptions_OVERWRITE_UNCOMMITTED_HELP)
      .addOption(OPT_IGNORE_UNCOMMITTED, Messages.UnloadWorkspaceCmdOptions_OVERWRITE_UNCOMMITTED_HELP)
      .addOption(OPT_DELETE, "D", "delete", Messages.UnloadWOrkspaceCmdOptions_DELETE_HELP, 0)
      .addOption(OPT_WORKSPACE, Messages.UnloadWorkspaceCmdOptions_WS_HELP)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_COMPONENTS, CommonOptions.OPT_COMPONENTS_SELECTOR_HELP, false)
      .addOption(OPT_PATHS, Messages.UnloadWorkspaceCmdOptions_ROOT_PATHS_HELP, false))
      .addOption(OPT_SELECTORS, Messages.UnloadWorkspaceCmdOptions_SELECTOR_HELP);
    
    return options;
  }
  
  private boolean isWorkspaceLoaded(IWorkspace ws, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    List<ISandboxWorkspace> wsList = RepoUtil.findWorkspacesInSandbox(client, config);
    for (ISandboxWorkspace wsFound : wsList) {
      if (wsFound.getWorkspaceItemId().equals(ws.getItemId().getUuidValue())) {
        return true;
      }
    }
    
    return false;
  }
  




  private Map<ITeamRepository, List<IComponent>> getLoadedComponents(ParmsWorkspace ws, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<ITeamRepository, List<IComponent>> repoToComps = new HashMap();
    
    ITeamRepository repo = RepoUtil.login(config, client, config.getConnectionInfo(repositoryUrl));
    

    Map<String, String> compList = RepoUtil.getComponentsInSandbox(workspaceItemId, client, config);
    List<IComponent> components = RepoUtil.getComponents(new ArrayList(compList.keySet()), repo, config);
    repoToComps.put(repo, components);
    
    return repoToComps;
  }
  



  private Map<ITeamRepository, List<IComponent>> getComponentsFromSelectors(List<IScmCommandLineArgument> selectors, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    Map<ITeamRepository, List<String>> repoToCompSelectors = new HashMap();
    ITeamRepository compRepo; for (IScmCommandLineArgument compSelector : selectors)
    {
      compRepo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
      List<String> compSelectors = (List)repoToCompSelectors.get(compRepo);
      if (compSelectors == null) {
        compSelectors = new ArrayList();
        repoToCompSelectors.put(compRepo, compSelectors);
      }
      compSelectors.add(compSelector.getItemSelector());
    }
    

    Map<ITeamRepository, List<IComponent>> repoToComps = new HashMap();
    for (Object entry : repoToCompSelectors.entrySet()) {
      List<IComponent> comps = RepoUtil.getComponents((List)((Map.Entry)entry).getValue(), (ITeamRepository)((Map.Entry)entry).getKey(), config);
      repoToComps.put((ITeamRepository)((Map.Entry)entry).getKey(), comps);
    }
    
    return repoToComps;
  }
  
  private ParmsWorkspaceComponent[] generateParmsWorkspaceComponent(ParmsWorkspace ws, Map<ITeamRepository, List<IComponent>> repoToComps, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    List<ISandboxWorkspace> wsInSandboxList = RepoUtil.findWorkspacesInSandbox(client, config);
    
    Map<ParmsWorkspace, List<String>> wsToComp = new HashMap();
    List<String> matchedComps = new ArrayList();
    
    int totalCompSelectors = 0;
    Iterator localIterator2;
    ITeamRepository wsRepo;
    for (Iterator localIterator1 = repoToComps.entrySet().iterator(); localIterator1.hasNext(); 
        


        localIterator2.hasNext())
    {
      Map.Entry<ITeamRepository, List<IComponent>> entry = (Map.Entry)localIterator1.next();
      totalCompSelectors += ((List)entry.getValue()).size();
      

      localIterator2 = wsInSandboxList.iterator(); continue;wsInSandbox = (ISandboxWorkspace)localIterator2.next();
      
      if ((ws == null) || (wsInSandbox.getWorkspaceItemId().equals(workspaceItemId)))
      {




        wsRepo = RepoUtil.getSharedRepository(
          RepoUtil.getRepoUri(config, client, wsInSandbox.getRepositoryId(), 
          Collections.singletonList(wsInSandbox)), false);
        ParmsWorkspace pw = new ParmsWorkspace(wsRepo.getRepositoryURI(), 
          wsInSandbox.getWorkspaceItemId());
        if (((ITeamRepository)entry.getKey()).getId().equals(wsRepo.getId())) {
          Map<String, String> compList = RepoUtil.getComponentsInSandbox(wsInSandbox.getWorkspaceItemId(), client, config);
          
          for (IComponent comp : (List)entry.getValue()) {
            for (String wsCompId : compList.keySet()) {
              if (comp.getItemId().getUuidValue().equals(wsCompId)) {
                List<String> wsCompIds = (List)wsToComp.get(pw);
                
                if (wsCompIds == null) {
                  wsCompIds = new ArrayList();
                  wsToComp.put(pw, wsCompIds);
                }
                
                wsCompIds.add(wsCompId);
                matchedComps.add(wsCompId);
                break;
              }
            }
          }
        }
      }
    }
    

    if (matchedComps.size() != totalCompSelectors) {
      IndentingPrintStream err = new IndentingPrintStream(config.getContext().stderr());
      err.println(Messages.UnloadCmd_INVALID_COMP_SELECTOR_LIST);
      for (wsInSandbox = repoToComps.entrySet().iterator(); wsInSandbox.hasNext(); 
          wsRepo.hasNext())
      {
        Object entry = (Map.Entry)wsInSandbox.next();
        wsRepo = ((List)((Map.Entry)entry).getValue()).iterator(); continue;IComponent comp = (IComponent)wsRepo.next();
        if (!matchedComps.contains(comp.getItemId().getUuidValue())) {
          err.indent().println(AliasUtil.selector(comp.getName(), comp.getItemId(), 
            ((ITeamRepository)((Map.Entry)entry).getKey()).getRepositoryURI(), RepoUtil.ItemType.COMPONENT));
        }
      }
      
      throw StatusHelper.ambiguousSelector(Messages.UnloadCmd_INVALID_COMP_SELECTOR);
    }
    

    List<ParmsWorkspaceComponent> parmsWsComp = new ArrayList();
    
    for (ISandboxWorkspace wsInSandbox = wsToComp.entrySet().iterator(); wsInSandbox.hasNext(); 
        wsRepo.hasNext())
    {
      Object entry = (Map.Entry)wsInSandbox.next();
      wsRepo = ((List)((Map.Entry)entry).getValue()).iterator(); continue;String compId = (String)wsRepo.next();
      ParmsWorkspaceComponent wsComp = new ParmsWorkspaceComponent();
      workspace = ((ParmsWorkspace)((Map.Entry)entry).getKey());
      componentItemId = compId;
      
      parmsWsComp.add(wsComp);
    }
    

    return (ParmsWorkspaceComponent[])parmsWsComp.toArray(new ParmsWorkspaceComponent[parmsWsComp.size()]);
  }
  
  private ParmsPath[] generateSharePaths(List<String> selectors, IFilesystemRestClient client, IScmClientConfiguration config) throws FileSystemException
  {
    List<ParmsPath> paths = new ArrayList();
    

    for (String selector : selectors) {
      ILocation absolutePath = SubcommandUtil.makeAbsolutePath(config, selector);
      if (!SubcommandUtil.exists(absolutePath, null)) {
        throw StatusHelper.disallowed(NLS.bind(Messages.AnnotateCmd_PathDoesNotExist, absolutePath.toOSString()));
      }
      
      File cfaRoot = SubcommandUtil.findAncestorCFARoot(absolutePath.toOSString());
      if (cfaRoot == null) {
        throw StatusHelper.argSyntax(NLS.bind(Messages.AnnotateCmd_PathIsNotShared, selector));
      }
      
      IRelativeLocation relativePath = absolutePath.getLocationRelativeTo(new PathLocation(cfaRoot.getAbsolutePath()));
      
      RepoUtil.loginOnPath(config, client, absolutePath);
      
      ParmsPath path = new ParmsPath();
      sandboxPath = cfaRoot.getAbsolutePath();
      relativePath = relativePath.toString();
      
      paths.add(path);
    }
    
    return (ParmsPath[])paths.toArray(new ParmsPath[paths.size()]);
  }
  
  private void generateUnloadParms(ParmsUnload parms, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if ((cli.hasOption(OPT_COMPONENTS.getId())) && (cli.hasOption(OPT_PATHS.getId()))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.DeliverCmd_USE_SINGLE_MODE_ARGUMENT, new String[] {
        OPT_COMPONENTS.toString(), OPT_PATHS.toString() }));
    }
    
    if (((cli.hasOption(OPT_COMPONENTS.getId())) || (cli.hasOption(OPT_PATHS.getId()))) && 
      (!cli.hasOption(OPT_SELECTORS.getId()))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.UnloadWorkspaceCmd_NO_SELECTORS, 
        cli.hasOption(OPT_COMPONENTS.getId()) ? OPT_COMPONENTS.toString() : OPT_PATHS.toString()));
    }
    
    ITeamRepository repo = null;
    ParmsWorkspace ws = null;
    
    if (cli.hasOption(OPT_WORKSPACE.getId())) {
      IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE.getId()), config);
      SubcommandUtil.validateArgument(wsSelector, RepoUtil.ItemType.WORKSPACE);
      
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, false, repo, config);
      

      if (!isWorkspaceLoaded(wsFound, client, config)) {
        throw StatusHelper.ambiguousSelector(NLS.bind(Messages.UnloadCmd_INVALID_WS, 
          wsSelector.getItemSelector()));
      }
      
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    }
    
    if (cli.hasOption(OPT_SELECTORS.getId())) {
      List<IScmCommandLineArgument> selectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SELECTORS.getId()), config);
      
      if (cli.hasOption(OPT_PATHS.getId())) {
        fullSharePaths = generateSharePaths(RepoUtil.getSelectors(selectors), client, config);
      } else {
        SubcommandUtil.validateArgument(selectors, RepoUtil.ItemType.COMPONENT);
        if ((ws != null) && (selectors != null)) {
          RepoUtil.validateItemRepos(RepoUtil.ItemType.COMPONENT, selectors, repo, config);
        }
        
        Map<ITeamRepository, List<IComponent>> repoToComps = getComponentsFromSelectors(selectors, client, config);
        unloadRequests = generateParmsWorkspaceComponent(ws, repoToComps, client, config);
      }
    }
    else {
      if (ws == null) {
        ws = RepoUtil.findWorkspaceInSandbox(null, null, client, config);
      }
      
      Map<ITeamRepository, List<IComponent>> repoToComps = getLoadedComponents(ws, client, config);
      unloadRequests = generateParmsWorkspaceComponent(ws, repoToComps, client, config);
      

      unloadedWorkspace = workspaceItemId;
    }
    
    deleteContent = Boolean.valueOf(cli.hasOption(OPT_DELETE));
    preoperationRefresh = SubcommandUtil.getPreopRefreshPolicy(config);
    pendingChangesDilemmaHandler = new ParmsPendingChangesDilemmaHandler();
    if ((cli.hasOption(OPT_OVERWRITE_UNCOMMITTED_DEPRECATED)) || (cli.hasOption(OPT_IGNORE_UNCOMMITTED))) {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "no";
    } else {
      pendingChangesDilemmaHandler.pendingChangesInstruction = "cancel";
    }
  }
  
  public void run() throws FileSystemException {
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    ParmsUnload parms = new ParmsUnload();
    generateUnloadParms(parms, client, config);
    

    if (((fullSharePaths != null) && (fullSharePaths.length > 0)) || (
      (unloadRequests != null) && (unloadRequests.length > 0))) {
      try {
        UnLoadResultDTO result = client.postUnloadCFA(parms, null);
        if (result.isCancelled()) {
          int noOfUncheckedInChanges = 0;
          List<ConfigurationWithUncheckedInChanges2DTO> configsWithUncheckedInChanges = result
            .getConfigurationsWithUncheckedInChanges();
          for (ConfigurationWithUncheckedInChanges2DTO configWithUncheckedInChanges : configsWithUncheckedInChanges) {
            noOfUncheckedInChanges += configWithUncheckedInChanges.getChangeCount();
          }
          if (noOfUncheckedInChanges > 0) {
            throw StatusHelper.uncheckedInChanges(NLS.bind(
              Messages.AcceptCmd2_UNCHECKEDIN_ITEMS_PRESENT, Integer.valueOf(noOfUncheckedInChanges), 
              OPT_IGNORE_UNCOMMITTED.getName()));
          }
        }
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.UnloadWorkspaceCmd_FAILURE, e, 
          new IndentingPrintStream(config.getContext().stderr()));
      }
    }
    

    if (unloadedWorkspace != null) {
      File cfaRootPath = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
      config.getSandboxHistoryRegistry().removeWorkspace(new PathLocation(cfaRootPath.getAbsolutePath()), unloadedWorkspace);
    }
    
    config.getContext().stdout().println(Messages.UnloadWorkspaceCmd_Success);
  }
}
