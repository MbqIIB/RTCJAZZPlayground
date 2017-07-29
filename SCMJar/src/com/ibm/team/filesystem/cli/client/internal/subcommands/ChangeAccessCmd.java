package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentSeed;
import com.ibm.team.filesystem.client.rest.parameters.ParmsItemHandle;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSetVersionablePermissionsRequest;
import com.ibm.team.filesystem.client.rest.parameters.ParmsVersionable;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.process.common.IAccessGroup;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.MutuallyExclusiveGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.SimpleGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.VersionablePermissionDeniedException;
import com.ibm.team.scm.common.internal.rest.IScmRestService;
import com.ibm.team.scm.common.internal.rest.dto.VersionableDTO;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.osgi.util.NLS;

public class ChangeAccessCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  IFilesystemRestClient client = null;
  
  public static final PositionalOptionDefinition OPT_ITEM = new PositionalOptionDefinition("item", 1, -1);
  public static final IOptionKey OPT_WORKSPACE = new OptionKey("workspace");
  public static final IOptionKey OPT_COMPONENT = new OptionKey("component");
  public static final IOptionKey OPT_COMPONENT_ACCESS = new OptionKey("componentaccess");
  public static final IOptionKey OPT_CONTRIBUTOR = new OptionKey("contributor");
  public static final IOptionKey OPT_PROJECTAREA = new OptionKey("projectarea");
  public static final IOptionKey OPT_TEAMAREA = new OptionKey("teamarea");
  public static final IOptionKey OPT_ACCESS_GROUP = new OptionKey("accessgroup");
  public static final IOptionKey OPT_APPLY_TO_CHILD_ITEMS = new OptionKey("apply-to-child-items");
  public ChangeAccessCmd() {}
  
  public static class WsComp {
    String wsId;
    String compId;
    
    public WsComp(String wsId, String compId) { this.wsId = wsId;
      this.compId = compId;
    }
    
    public String getWsId() {
      return wsId;
    }
    
    public String getCompId() {
      return compId;
    }
  }
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(OPT_APPLY_TO_CHILD_ITEMS, null, "apply-to-child-items", Messages.ChangeAccessCmdOptions_APPLY_TO_CHILD_ITEMS, 0)
      .addOption(new SimpleGroup(false)
      .addOption(new NamedOptionDefinition(OPT_WORKSPACE, "w", "workspace", 1), Messages.ChangeAccessCmdOption_WORKSPACE, true)
      .addOption(new NamedOptionDefinition(OPT_COMPONENT, "C", "component", 1), CommonOptions.OPT_COMPONENT_SELECTOR_HELP, true))
      .addOption(new MutuallyExclusiveGroup()
      .addOption(OPT_CONTRIBUTOR, null, "contrib", Messages.ChangeAccessCmdOptions_CONTRIBUTOR, 1, false)
      .addOption(OPT_PROJECTAREA, null, "projectarea", Messages.ChangeAccessCmdOptions_PROJECTAREA, 1, false)
      .addOption(OPT_TEAMAREA, null, "teamarea", Messages.ChangeAccessCmdOptions_TEAMAREA, 1, false)
      .addOption(OPT_COMPONENT_ACCESS, null, "componentaccess", Messages.ChangeAccessCmdOptions_COMPONENT_ACCESS, 0, false)
      .addOption(OPT_ACCESS_GROUP, null, "accessgroup", Messages.ChangeAccessCmdOptions_ACCESS_GROUP, 1, false))
      .addOption(OPT_ITEM, Messages.ChangeAccessCmdOptions_ITEM);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    if (((cli.hasOption(OPT_WORKSPACE)) && (!cli.hasOption(OPT_COMPONENT))) || (
      (!cli.hasOption(OPT_WORKSPACE)) && (cli.hasOption(OPT_COMPONENT)))) {
      throw StatusHelper.argSyntax(Messages.ChangeAccessCmd_SPECIFY_WSCOMP);
    }
    
    int count = 0;
    if (cli.hasOption(OPT_COMPONENT_ACCESS)) count++;
    if (cli.hasOption(OPT_CONTRIBUTOR)) count++;
    if (cli.hasOption(OPT_PROJECTAREA)) count++;
    if (cli.hasOption(OPT_TEAMAREA)) count++;
    if (cli.hasOption(OPT_ACCESS_GROUP)) { count++;
    }
    
    if (count > 1) {
      throw StatusHelper.argSyntax(NLS.bind(Messages.Common_SPECIFY_1_OF_5_ARGUMENTS, 
        new Object[] {
        cli.getDefinition().getOption(OPT_COMPONENT_ACCESS).getName(), 
        cli.getDefinition().getOption(OPT_CONTRIBUTOR).getName(), 
        cli.getDefinition().getOption(OPT_PROJECTAREA).getName(), 
        cli.getDefinition().getOption(OPT_TEAMAREA).getName(), 
        cli.getDefinition().getOption(OPT_ACCESS_GROUP).getName() }));
    }
    
    client = SubcommandUtil.setupDaemon(config);
    List<String> items = cli.getOptions(OPT_ITEM);
    
    Map<ITeamRepository, Map<WsComp, List<ParmsVersionable>>> repoToCompToItems = 
      new HashMap();
    IScmCommandLineArgument compSelector;
    if (cli.hasOption(OPT_WORKSPACE)) {
      IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_WORKSPACE), config);
      compSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_COMPONENT), config);
      repoToCompToItems = getRemoteItems(wsSelector, compSelector, items);
    } else {
      repoToCompToItems = getLocalItems(items);
    }
    
    for (Map.Entry<ITeamRepository, Map<WsComp, List<ParmsVersionable>>> entry : repoToCompToItems.entrySet()) {
      changeAccess((ITeamRepository)entry.getKey(), (Map)entry.getValue(), cli);
    }
  }
  
  private Map<ITeamRepository, Map<WsComp, List<ParmsVersionable>>> getRemoteItems(IScmCommandLineArgument wsSelector, IScmCommandLineArgument compSelector, List<String> items) throws FileSystemException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
    IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    String wsId = wsFound.getItemId().getUuidValue();
    
    ParmsWorkspace ws = new ParmsWorkspace(repo.getRepositoryURI(), wsId);
    WorkspaceComponentDTO wsComp = RepoUtil.getComponent(ws, compSelector.getItemSelector(), client, config);
    String compId = wsComp.getItemId();
    
    List<ParmsVersionable> verItems = new ArrayList();
    IScmRestService scmService = (IScmRestService)((IClientLibraryContext)repo).getServiceInterface(IScmRestService.class);
    
    for (String item : items) {
      String[] path = StringUtil.splitEscapedPath(item);
      String remotePath = StringUtil.createPathString(path);
      
      VersionableDTO verDTO = RepoUtil.getVersionableByPath(scmService, wsId, compId, remotePath, config);
      if (!verItems.contains(verDTO.getVersionable().getItemId().getUuidValue())) {
        ParmsVersionable ver = new ParmsVersionable(verDTO.getVersionable());
        verItems.add(ver);
      }
    }
    
    Map<WsComp, List<ParmsVersionable>> compToItems = new HashMap();
    compToItems.put(new WsComp(wsId, compId), verItems);
    
    Object repoToCompToItems = 
      new HashMap();
    ((Map)repoToCompToItems).put(repo, compToItems);
    
    return repoToCompToItems;
  }
  
  private Map<ITeamRepository, Map<WsComp, List<ParmsVersionable>>> getLocalItems(List<String> items) throws FileSystemException
  {
    List<ResourcePropertiesDTO> resProps = RepoUtil.getResourceProperties(items, client, config);
    
    Map<String, ITeamRepository> repoUrlToObj = new HashMap();
    Map<ITeamRepository, Map<WsComp, List<ParmsVersionable>>> repoToCompToItems = 
      new HashMap();
    Map<String, WsComp> compToWsComp = new HashMap();
    
    for (ResourcePropertiesDTO resProp : resProps) {
      String repositoryUrl = config.getRepositoryURI(resProp.getShare().getRepositoryId(), null);
      ITeamRepository repo = (ITeamRepository)repoUrlToObj.get(repositoryUrl);
      if (repo == null) {
        repo = RepoUtil.getSharedRepository(repositoryUrl, true);
        repoUrlToObj.put(repositoryUrl, repo);
      }
      
      Map<WsComp, List<ParmsVersionable>> compToItems = (Map)repoToCompToItems.get(repo);
      if (compToItems == null) {
        compToItems = new HashMap();
        repoToCompToItems.put(repo, compToItems);
      }
      
      WsComp wsComp = (WsComp)compToWsComp.get(resProp.getShare().getComponentItemId());
      if (wsComp == null) {
        wsComp = new WsComp(resProp.getShare().getContextItemId(), resProp.getShare().getComponentItemId());
      }
      List<ParmsVersionable> verItems = (List)compToItems.get(wsComp);
      if (verItems == null) {
        verItems = new ArrayList();
        compToItems.put(wsComp, verItems);
      }
      ParmsVersionable ver = new ParmsVersionable();
      itemId = resProp.getItemId();
      versionableItemType = resProp.getVersionableItemType();
      verItems.add(ver);
    }
    
    return repoToCompToItems;
  }
  
  private void changeAccess(ITeamRepository repo, Map<WsComp, List<ParmsVersionable>> compIdToItems, ICommandLine cli) throws FileSystemException
  {
    ParmsItemHandle parmsContext = getAccessContext(repo, cli);
    
    for (Map.Entry<WsComp, List<ParmsVersionable>> entry : compIdToItems.entrySet()) {
      ParmsSetVersionablePermissionsRequest parms = new ParmsSetVersionablePermissionsRequest();
      repositoryUrl = repo.getRepositoryURI();
      readContext = parmsContext;
      componentItemId = ((WsComp)entry.getKey()).getCompId();
      versionables = ((ParmsVersionable[])((List)entry.getValue()).toArray(new ParmsVersionable[((List)entry.getValue()).size()]));
      
      if (cli.hasOption(OPT_APPLY_TO_CHILD_ITEMS)) {
        ParmsComponentSeed seed = new ParmsComponentSeed();
        repositoryUrl = repo.getRepositoryURI();
        itemId = ((WsComp)entry.getKey()).getWsId();
        itemTypeId = "workspace";
        
        seed = seed;
      }
      try
      {
        client.postSetVersionablePermissions(parms, null);
      } catch (VersionablePermissionDeniedException e) {
        throw StatusHelper.permissionFailure(e.getLocalizedMessage() != null ? 
          e.getLocalizedMessage() : Messages.Common_VERSIONABLE_PERMISSSION_DENIED);
      } catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(Messages.ChangeAccessCmd_FAILURE, e, 
          new IndentingPrintStream(config.getContext().stderr()), repo.getRepositoryURI());
      }
    }
    
    config.getContext().stdout().println(Messages.ChangeAccessCmd_SUCCESS);
  }
  
  private ParmsItemHandle getAccessContext(ITeamRepository repo, ICommandLine cli) throws FileSystemException {
    ParmsItemHandle context = null;
    
    if (!cli.hasOption(OPT_COMPONENT_ACCESS))
    {
      if (cli.hasOption(OPT_CONTRIBUTOR)) {
        String contribName = cli.getOption(OPT_CONTRIBUTOR);
        IContributor contrib = RepoUtil.getContributor(contribName, repo, config);
        context = new ParmsItemHandle(contrib);
      } else if (cli.hasOption(OPT_TEAMAREA)) {
        String teamAreaSelector = cli.getOption(OPT_TEAMAREA);
        ITeamArea teamArea = RepoUtil.getTeamArea(teamAreaSelector, null, config, repo);
        if (teamArea == null) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.ListCmd_TeamAreaNotFound, teamAreaSelector));
        }
        context = new ParmsItemHandle(teamArea);
      } else if (cli.hasOption(OPT_PROJECTAREA)) {
        String projAreaSelector = cli.getOption(OPT_PROJECTAREA);
        IProjectArea projArea = RepoUtil.getProjectArea(repo, projAreaSelector, config);
        if (projArea == null) {
          throw StatusHelper.itemNotFound(NLS.bind(Messages.ListCmd_NOPROJECTAREA, projAreaSelector));
        }
        context = new ParmsItemHandle(projArea);
      } else if (cli.hasOption(OPT_ACCESS_GROUP)) {
        IScmCommandLineArgument accessGroupSelector = ScmCommandLineArgument.create(cli.getOptionValue(OPT_ACCESS_GROUP, null), config);
        SubcommandUtil.validateArgument(accessGroupSelector, RepoUtil.ItemType.ACCESSGROUP);
        IAccessGroup accessGroup = RepoUtil.getAccessGroup(accessGroupSelector, repo, config);
        if (accessGroup == null) {
          throw StatusHelper.argSyntax(NLS.bind(Messages.WorkspacePropertiesCmd_INVALID_ACCESS_GROUP, accessGroupSelector));
        }
        
        context = new ParmsItemHandle(accessGroup);
        itemId = accessGroup.getGroupContextId().getUuidValue();
      }
    }
    return context;
  }
}
