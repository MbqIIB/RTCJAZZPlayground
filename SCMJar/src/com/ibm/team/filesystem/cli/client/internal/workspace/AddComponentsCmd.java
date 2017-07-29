package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentSeed;
import com.ibm.team.filesystem.client.rest.parameters.ParmsConfigurationChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsDeletedContentDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxUpdateDilemmaHandler;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspaceUpdateDilemmaHandler;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConfigurationDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ConnectionDescriptorDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.dilemma.SandboxUpdateDilemmaDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.PutWorkspaceResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IBaselineHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IWorkspace;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.osgi.util.NLS;

public class AddComponentsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public AddComponentsCmd() {}
  
  public static final IOptionKey OPT_COMPONENTS = new OptionKey("components", "@");
  public static final NamedOptionDefinition OPT_FLOW_FROM_WORKSPACE = new NamedOptionDefinition("s", "source-workspace", 1, "@");
  public static final NamedOptionDefinition OPT_FLOW_FROM_BASELINE = new NamedOptionDefinition("b", "baseline", 1, "@");
  public static final NamedOptionDefinition OPT_ALLOW_MULTIPLE_HIERARCHY = new NamedOptionDefinition("m", "multiple-hierarchy", 0);
  public static final NamedOptionDefinition OPT_SKIP_INACCESSIBLE_COMPONENTS = new NamedOptionDefinition("i", "skip-inaccessible", 0);
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(OPT_ALLOW_MULTIPLE_HIERARCHY, Messages.AddComponentsCmdOptions_MULTI_HELP)
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_FLOW_FROM_WORKSPACE, "s", "source-workspace", Messages.AddComponentCommand_WorkspaceForInitialHistory, 1, false)
      .addOption(OPT_FLOW_FROM_BASELINE, "b", "baseline", Messages.AddComponentCommand_BaselineForInitialHistory, 1, false))
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.AddComponentCommand_WorkspaceToAddComponents)
      .addOption(OPT_SKIP_INACCESSIBLE_COMPONENTS, Messages.AddComponentCommand_INACCESSIBLE_HELP)
      .addOption(new PositionalOptionDefinition(OPT_COMPONENTS, "components", 1, -1, "@"), Messages.AddComponentCommand_ComponentsToAdd);
    
    return options;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if ((cli.hasOption(OPT_FLOW_FROM_WORKSPACE)) && (cli.hasOption(OPT_FLOW_FROM_BASELINE))) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.AddComponentCommand_INVALID_USE_OF_OPTIONS, OPT_FLOW_FROM_WORKSPACE.toString(), OPT_FLOW_FROM_BASELINE.toString()));
    }
    
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    

    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    

    IWorkspace ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    

    List<IScmCommandLineArgument> componentSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_COMPONENTS), config);
    SubcommandUtil.validateArgument(componentSelectors, RepoUtil.ItemType.COMPONENT);
    

    Map<ITeamRepository, Set<String>> repoToCompSelectors = new HashMap();
    for (IScmCommandLineArgument compSelector : componentSelectors)
    {
      compRepo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
      Set<String> compSelectors = (Set)repoToCompSelectors.get(compRepo);
      if (compSelectors == null) {
        compSelectors = new HashSet();
        repoToCompSelectors.put(compRepo, compSelectors);
      }
      compSelectors.add(compSelector.getItemSelector());
    }
    

    Map<IComponent, ITeamRepository> componentsToRepo = new HashMap();
    Iterator localIterator2; for (ITeamRepository compRepo = repoToCompSelectors.entrySet().iterator(); compRepo.hasNext(); 
        
        localIterator2.hasNext())
    {
      Object entry = (Map.Entry)compRepo.next();
      List<IComponent> comps = RepoUtil.getComponents(new ArrayList((Collection)((Map.Entry)entry).getValue()), (ITeamRepository)((Map.Entry)entry).getKey(), config);
      localIterator2 = comps.iterator(); continue;IComponent comp = (IComponent)localIterator2.next();
      componentsToRepo.put(comp, (ITeamRepository)((Map.Entry)entry).getKey());
    }
    

    RepoUtil.ItemType type = null;
    IScmCommandLineArgument itemSelector = null;
    
    if (cli.hasOption(OPT_FLOW_FROM_WORKSPACE)) {
      type = RepoUtil.ItemType.WORKSPACE;
      itemSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_FLOW_FROM_WORKSPACE), config);
      SubcommandUtil.validateArgument(itemSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    }
    else if (cli.hasOption(OPT_FLOW_FROM_BASELINE)) {
      type = RepoUtil.ItemType.BASELINE;
      itemSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_FLOW_FROM_BASELINE), config);
      SubcommandUtil.validateArgument(itemSelector, RepoUtil.ItemType.BASELINE);
    }
    

    ParmsWorkspace targetWs = new ParmsWorkspace(repo.getRepositoryURI(), ws.getItemId().getUuidValue());
    WorkspaceDetailsDTO targetWsDetails = 
      (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(targetWs), client, config).get(0);
    Object targetWsComps = targetWsDetails.getComponents();
    

    Map<String, WorkspaceComponentDTO> targetWsCompIds = new HashMap(((List)targetWsComps).size());
    for (WorkspaceComponentDTO comp : (List)targetWsComps) {
      targetWsCompIds.put(comp.getItemId(), comp);
    }
    

    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    boolean printSkipHeader = true;
    Map<IComponent, ITeamRepository> filteredComponentsToRepo = new HashMap();
    for (Map.Entry<IComponent, ITeamRepository> entry : componentsToRepo.entrySet()) {
      WorkspaceComponentDTO comp = (WorkspaceComponentDTO)targetWsCompIds.get(((IComponent)entry.getKey()).getItemId().getUuidValue());
      if (comp == null) {
        filteredComponentsToRepo.put((IComponent)entry.getKey(), (ITeamRepository)entry.getValue());
      } else {
        if (printSkipHeader) {
          out.println(Messages.AddComponentCommand_SkippingComponentAlreadyFound);
          printSkipHeader = false;
        }
        out.indent().println(AliasUtil.selector(comp.getName(), UUID.valueOf(comp.getItemId()), 
          repositoryUrl, RepoUtil.ItemType.COMPONENT));
      }
    }
    
    if (!filteredComponentsToRepo.isEmpty()) {
      putWorkspace(config, cli, client, repo, ws, filteredComponentsToRepo, type, itemSelector);
    }
  }
  








  private void putWorkspace(IScmClientConfiguration config, ICommandLine cli, IFilesystemRestClient client, ITeamRepository repo, IWorkspace workspace, Map<IComponent, ITeamRepository> compToRepo, RepoUtil.ItemType type, IScmCommandLineArgument itemSelector)
    throws FileSystemException
  {
    UUID wsUuid = null;
    Set<UUID> componentUuid = null;
    
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    configurationChanges = new ParmsConfigurationChanges();
    configurationChanges.sandboxUpdateDilemmaHandler = new ParmsSandboxUpdateDilemmaHandler();
    configurationChanges.sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler = new ParmsDeletedContentDilemmaHandler();
    configurationChanges.sandboxUpdateDilemmaHandler.deletedContentDilemmaHandler.deletedContentDirection = "continue";
    configurationChanges.hierarchicalOperation = true;
    ParmsWorkspaceUpdateDilemmaHandler parmsWorkspaceUpdateDilemmaHandler = new ParmsWorkspaceUpdateDilemmaHandler();
    componentFlowDirection = "continue";
    disconnectedComponentsDirection = "continue";
    noBackupBaselineDirection = "continue";
    multipleParticipantsDirection = "continue";
    if (cli.hasOption(OPT_SKIP_INACCESSIBLE_COMPONENTS)) {
      inaccessibleComponentsNotInSeedDirection = "continue";
    } else {
      inaccessibleComponentsNotInSeedDirection = "cancel";
    }
    
    configurationChanges.workspaceUpdateDilemmaHandler = parmsWorkspaceUpdateDilemmaHandler;
    if (cli.hasOption(OPT_ALLOW_MULTIPLE_HIERARCHY)) {
      componentInMultipleHierarchiesDirection = "continue";
    } else {
      componentInMultipleHierarchiesDirection = "cancel";
    }
    
    workspace = new ParmsWorkspace();
    workspace.repositoryUrl = repo.getRepositoryURI();
    workspace.workspaceItemId = workspace.getItemId().getUuidValue();
    
    if (type == RepoUtil.ItemType.WORKSPACE) {
      ITeamRepository repo2 = RepoUtil.loginUrlArgAncestor(config, client, itemSelector);
      IWorkspace ws = RepoUtil.getWorkspace(itemSelector.getItemSelector(), true, true, repo2, config);
      wsUuid = ws.getItemId();
      

      ParmsWorkspace sourceWs = new ParmsWorkspace(repo2.getRepositoryURI(), ws.getItemId().getUuidValue());
      WorkspaceDetailsDTO sourceWsDetails = 
        (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(sourceWs), client, config).get(0);
      List<WorkspaceComponentDTO> sourceWsComps = sourceWsDetails.getComponents();
      
      componentUuid = new HashSet();
      for (WorkspaceComponentDTO component : sourceWsComps) {
        componentUuid.add(UUID.valueOf(component.getItemId()));
      }
    }
    
    List<ParmsComponentChange> changeList = new LinkedList();
    
    List<IComponent> comps = new ArrayList(compToRepo.keySet());
    for (int i = 0; i < comps.size(); i++) {
      ParmsComponentChange change = new ParmsComponentChange();
      cmd = "addComponent";
      IComponent comp = (IComponent)comps.get(i);
      componentItemId = comp.getItemId().getUuidValue();
      

      if (type != null) {
        seed = new ParmsComponentSeed();
        seed.repositoryUrl = ((ITeamRepository)compToRepo.get(comp)).getRepositoryURI();
        
        if (type == RepoUtil.ItemType.WORKSPACE)
        {
          if (!componentUuid.contains(comp.getItemId())) {
            continue;
          }
          
          seed.itemTypeId = "workspace";
          seed.itemId = wsUuid.getUuidValue();
        }
        else {
          seed.itemTypeId = "baseline";
          ITeamRepository blRepo = (ITeamRepository)compToRepo.get(comp);
          IBaseline baseline = RepoUtil.getBaseline(itemSelector.getItemSelector(), comp.getItemId().getUuidValue(), 
            comp.getName(), blRepo, client, config);
          seed.itemId = baseline.getItemId().getUuidValue();
        }
        

      }
      else if (!((ITeamRepository)compToRepo.get(comp)).getId().equals(repo.getId()))
      {
        seed = new ParmsComponentSeed();
        seed.repositoryUrl = ((ITeamRepository)compToRepo.get(comp)).getRepositoryURI();
        
        seed.itemTypeId = "baseline";
        seed.itemId = comp.getInitialBaseline().getItemId().getUuidValue();
      }
      

      changeList.add(change);
    }
    

    configurationChanges.components = ((ParmsComponentChange[])changeList.toArray(new ParmsComponentChange[changeList.size()]));
    
    try
    {
      result = client.postPutWorkspace(parms, null);
    } catch (TeamRepositoryException e) { PutWorkspaceResultDTO result;
      throw StatusHelper.wrap(Messages.AddComponentCommand_CannotAddComponents, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
    PutWorkspaceResultDTO result;
    if (result.isCancelled()) {
      if ((result.getSelectedComponentsWithMultipleParticipants().size() > 0) || 
        (result.getSubcomponentsWithMultipleParticipants().size() > 0)) {
        throw StatusHelper.componentsInMultipleHierarchies(NLS.bind(Messages.AddComponentsCmd_MULTI_HIERARCHY, OPT_ALLOW_MULTIPLE_HIERARCHY.getName()));
      }
      if (result.getInaccessibleComponentsNotInSeed().size() > 0) {
        throw StatusHelper.inaccessibleComponentsNotInSeed(NLS.bind(Messages.AddComponentsCmd_INACCESSIBLE_COMPONENTS_NOT_IN_SEED, OPT_ALLOW_MULTIPLE_HIERARCHY.getName()));
      }
    }
    
    printResult(config, comps, result);
  }
  
  private void printResult(IScmClientConfiguration config, List<IComponent> values, PutWorkspaceResultDTO result) throws FileSystemException
  {
    if (!result.isSetComponentsAdded()) {
      throw StatusHelper.failure(Messages.AddComponentCommand_CannotAddComponents, null);
    }
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    final Map<UUID, String> uuidToName = new HashMap();
    for (IComponent component : values) {
      uuidToName.put(component.getItemId(), component.getName());
    }
    List<ConfigurationDescriptorDTO> componentsAdded = new ArrayList(result.getComponentsAdded());
    
    if (componentsAdded.size() > 0) {
      out.println(Messages.AddComponentCommand_ComponentSuccessfullyAdded);
      
      getNamesForNewlyAddedComponents(config, result.getWorkspace().getRepositoryURL(), componentsAdded, uuidToName);
      
      Collections.sort(componentsAdded, new Comparator()
      {
        public int compare(ConfigurationDescriptorDTO object1, ConfigurationDescriptorDTO object2)
        {
          UUID uuid1 = UUID.valueOf(object1.getComponentItemId());
          UUID uuid2 = UUID.valueOf(object2.getComponentItemId());
          String name1 = (String)uuidToName.get(uuid1);
          String name2 = (String)uuidToName.get(uuid2);
          return name1.compareTo(name2);
        }
        

      });
      IndentingPrintStream componentsOutput = out.indent();
      for (ConfigurationDescriptorDTO componentDTO : componentsAdded) {
        UUID componentItemId = UUID.valueOf(componentDTO.getComponentItemId());
        
        componentsOutput.println(AliasUtil.selector((String)uuidToName.get(componentItemId), componentItemId, 
          componentDTO.getConnection().getRepositoryURL(), RepoUtil.ItemType.COMPONENT));
      }
    }
    
    if (result.getSandboxUpdateDilemma().getDeletedContentShareables().size() > 0) {
      SubcommandUtil.showDeletedContent(result.getSandboxUpdateDilemma().getDeletedContentShareables(), out);
    }
  }
  



  private void getNamesForNewlyAddedComponents(IScmClientConfiguration config, String repositoryURL, List<ConfigurationDescriptorDTO> componentsAdded, Map<UUID, String> uuidToName)
    throws FileSystemException
  {
    List<String> componentUuids = new ArrayList();
    for (ConfigurationDescriptorDTO componentDTO : componentsAdded) {
      componentUuids.add(componentDTO.getComponentItemId());
    }
    List<IComponent> components = RepoUtil.getComponents(componentUuids, RepoUtil.getSharedRepository(repositoryURL, false), config);
    for (IComponent component : components) {
      uuidToName.put(component.getItemId(), component.getName());
    }
  }
}
