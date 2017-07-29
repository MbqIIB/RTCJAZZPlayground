package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentHierarchyChange;
import com.ibm.team.filesystem.client.rest.parameters.ParmsPutWorkspace;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceDetailsDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.ComponentHierarchyUpdateResultDTO;
import com.ibm.team.filesystem.common.internal.rest.client.workspace.PutWorkspaceResultDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.PermissionDeniedException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.InvalidStreamOperationException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;






public abstract class UpdateSubcomponentsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final IOptionKey OPT_PARENT_COMPONENT = new OptionKey("parent-component");
  public static final IOptionKey OPT_SUBCOMPONENTS = new OptionKey("subcomponents");
  private String successMessage;
  
  public Options getOptions() throws ConflictingOptionException { Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(new PositionalOptionDefinition(CommonOptions.OPT_WORKSPACE, "workspace", 1, 1, "@"), Messages.UpdateSubcomponentsCmd_WORKSPACE_HELP_TEXT)
      .addOption(new PositionalOptionDefinition(OPT_PARENT_COMPONENT, "parent-component", 1, 1), parentComponentOptionHelpText)
      .addOption(new PositionalOptionDefinition(OPT_SUBCOMPONENTS, "subcomponents", 1, -1), subcomponentOptionHelpText);
    return options;
  }
  




  private String unchangedMessage;
  



  public UpdateSubcomponentsCmd(String parentComponentOptionHelpText, String subcomponentsOptionHelpText, String successMessage, String unchangedMessage)
  {
    this.parentComponentOptionHelpText = parentComponentOptionHelpText;
    subcomponentOptionHelpText = subcomponentsOptionHelpText;
    this.successMessage = successMessage;
    this.unchangedMessage = unchangedMessage;
  }
  
  public void run() throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(CommonOptions.OPT_WORKSPACE), config);
    SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
    

    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    

    IWorkspace workspace = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    

    ScmCommandLineArgument parentComponentSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_PARENT_COMPONENT), config);
    IComponent parentComponent = RepoUtil.getComponent(parentComponentSelector.getItemSelector(), repo, config);
    







    List<IComponentHandle> subcomponents = getSubcomponentsFromSelectors(cli, repo);
    









    PutWorkspaceResultDTO result = putWorkspace(repo, workspace, parentComponent, subcomponents, client);
    

    String changesetUuid = result.getComponentHierarchyUpdateResult().getAffectedChangeSetItemId();
    if (changesetUuid == null) {
      throwWorkspaceUnchanged(repo, parentComponent);
    }
    

    validateUpdatedSubcomponents(repo, parentComponent, subcomponents, result);
    

    printResult(repo, parentComponent, subcomponents, changesetUuid, config);
  }
  




  private String parentComponentOptionHelpText;
  



  private String subcomponentOptionHelpText;
  


  private List<IComponent> validateComponentsInWorkspace(ITeamRepository repo, IWorkspace workspace, List<IComponent> components, IFilesystemRestClient client)
    throws FileSystemException
  {
    Set<String> componentUuids = getWorkspaceComponents(repo, workspace, client);
    
    List<IComponent> componentsNotInWorkspace = new ArrayList();
    for (IComponent component : components) {
      if (!componentUuids.contains(component.getItemId().getUuidValue())) {
        componentsNotInWorkspace.add(component);
      }
    }
    
    return componentsNotInWorkspace;
  }
  
  private Set<String> workspaceComponentUuids = null;
  











  private Set<String> getWorkspaceComponents(ITeamRepository repo, IWorkspace workspace, IFilesystemRestClient client)
    throws FileSystemException
  {
    if (workspaceComponentUuids == null) {
      ParmsWorkspace parmsWorkspace = new ParmsWorkspace(repo.getRepositoryURI(), workspace.getItemId().getUuidValue());
      WorkspaceDetailsDTO workspaceDetailsDto = (WorkspaceDetailsDTO)RepoUtil.getWorkspaceDetails(Collections.singletonList(parmsWorkspace), client, config).get(0);
      List<WorkspaceComponentDTO> workspaceComponentDtos = workspaceDetailsDto.getComponents();
      
      workspaceComponentUuids = new HashSet();
      for (WorkspaceComponentDTO workspaceComponentDto : workspaceComponentDtos) {
        workspaceComponentUuids.add(workspaceComponentDto.getItemId());
      }
    }
    return workspaceComponentUuids;
  }
  








  private List<IComponentHandle> getSubcomponentsFromSelectors(ICommandLine cli, ITeamRepository repo)
    throws FileSystemException
  {
    List<IScmCommandLineArgument> componentSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SUBCOMPONENTS), config);
    List<String> compSelectors = RepoUtil.getSelectors(componentSelectors);
    Map<UUID, IComponentHandle> uniqueSubcomponents = new HashMap(componentSelectors.size());
    for (String compSelector : compSelectors) {
      Map<UUID, IComponentHandle> componentsFromSelector = RepoUtil.getComponentByLookupUuidAliasAndName(compSelector, repo, config);
      try {
        IComponent component = RepoUtil.getComponent(compSelector, componentsFromSelector, repo, config);
        uniqueSubcomponents.put(component.getItemId(), component);
      } catch (CLIFileSystemClientException e) {
        if ((e.getCause() instanceof PermissionDeniedException)) {
          IComponentHandle componentHandle = (IComponentHandle)componentsFromSelector.values().iterator().next();
          uniqueSubcomponents.put(componentHandle.getItemId(), componentHandle);
        } else {
          throw e;
        }
      }
    }
    
    List<IComponentHandle> subcomponents = new ArrayList(uniqueSubcomponents.values());
    return subcomponents;
  }
  












  private PutWorkspaceResultDTO putWorkspace(ITeamRepository repo, IWorkspace workspace, IComponent parentComponent, List<IComponentHandle> subcomponents, IFilesystemRestClient client)
    throws CLIFileSystemClientException, FileSystemException
  {
    ParmsPutWorkspace parms = createPutWorkspaceParms(repo, workspace, parentComponent, subcomponents);
    try
    {
      return client.postPutWorkspace(parms, null);
    } catch (InvalidStreamOperationException localInvalidStreamOperationException) {
      throw StatusHelper.disallowed(Messages.UpdateSubcomponentsCmd_INVALID_STREAM_OPERATION);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.UpdateSubcomponentsCmd_COULD_NOT_UPDATE, e, 
        new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
    }
  }
  









  private ParmsPutWorkspace createPutWorkspaceParms(ITeamRepository repo, IWorkspace workspace, IComponent parentComponent, List<IComponentHandle> subcomponents)
  {
    ParmsPutWorkspace parms = new ParmsPutWorkspace();
    workspace = new ParmsWorkspace();
    workspace.repositoryUrl = repo.getRepositoryURI();
    workspace.workspaceItemId = workspace.getItemId().getUuidValue();
    componentHierarchyChanges = new ParmsComponentHierarchyChange();
    componentHierarchyChanges.parentComponentUuid = parentComponent.getItemId().getUuidValue();
    String[] subcomponentUuids = new String[subcomponents.size()];
    for (int i = 0; i < subcomponents.size(); i++) {
      IComponentHandle subcomponent = (IComponentHandle)subcomponents.get(i);
      subcomponentUuids[i] = subcomponent.getItemId().getUuidValue();
    }
    setSubcomponentsOnParms(componentHierarchyChanges, subcomponentUuids);
    return parms;
  }
  







  protected abstract void setSubcomponentsOnParms(ParmsComponentHierarchyChange paramParmsComponentHierarchyChange, String[] paramArrayOfString);
  






  private void validateUpdatedSubcomponents(ITeamRepository repo, IComponent parentComponent, List<IComponentHandle> specifiedSubcomponents, PutWorkspaceResultDTO result)
    throws CLIFileSystemClientException
  {
    Map<UUID, IComponentHandle> intersection = getUpdatedSubcomponentsIntersection(specifiedSubcomponents, result);
    validateUpdatedSubcomponents(repo, parentComponent, specifiedSubcomponents, intersection);
  }
  






  private Map<UUID, IComponentHandle> getUpdatedSubcomponentsIntersection(List<IComponentHandle> specifiedSubcomponents, PutWorkspaceResultDTO result)
  {
    Set<String> updatedChildrenUuids = new HashSet();
    for (String childUuid : result.getComponentHierarchyUpdateResult().getUpdatedChildrenItemIds()) {
      updatedChildrenUuids.add(childUuid);
    }
    
    Map<UUID, IComponentHandle> intersection = new HashMap();
    for (IComponentHandle component : specifiedSubcomponents) {
      UUID itemId = component.getItemId();
      if (updatedChildrenUuids.contains(itemId.getUuidValue())) {
        intersection.put(itemId, component);
      }
    }
    return intersection;
  }
  





  private void throwWorkspaceUnchanged(ITeamRepository repo, IComponent parentComponent)
    throws CLIFileSystemClientException
  {
    config.getContext().stdout().println(NLS.bind(unchangedMessage, 
      AliasUtil.selector(parentComponent.getName(), parentComponent.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT), new Object[0]));
    StatusHelper.workspaceUnchanged();
  }
  








  public abstract void validateUpdatedSubcomponents(ITeamRepository paramITeamRepository, IComponent paramIComponent, List<IComponentHandle> paramList, Map<UUID, IComponentHandle> paramMap)
    throws CLIFileSystemClientException;
  







  private void printResult(ITeamRepository repo, IComponent parentComponent, List<IComponentHandle> subcomponents, String changesetUuid, IScmClientConfiguration config)
    throws FileSystemException
  {
    IndentingPrintStream printStream = new IndentingPrintStream(config.getContext().stdout());
    printStream.print(NLS.bind(successMessage, 
      AliasUtil.selector(parentComponent.getName(), parentComponent.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT), new Object[] {
      AliasUtil.alias(UUID.valueOf(changesetUuid), repo.getRepositoryURI(), RepoUtil.ItemType.CHANGESET) }));
    printStream = printStream.indent();
    for (IComponentHandle componentHandle : subcomponents) {
      String name = null;
      if ((componentHandle instanceof IComponent)) {
        IComponent component = (IComponent)componentHandle;
        name = component.getName();
      } else {
        name = componentHandle.getItemId().getUuidValue();
      }
      printStream.println(AliasUtil.selector(name, componentHandle.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT));
    }
  }
}
