package com.ibm.team.filesystem.cli.client.internal.listcommand;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.CommandLineCore;
import com.ibm.team.filesystem.cli.core.internal.ScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.ChangeSetStateFactory;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeFolderSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSetSyncDTO;
import com.ibm.team.filesystem.common.internal.rest.client.sync.ChangeSyncDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IOptionSource;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ContinuousGroup;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IOptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.Options;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.exceptions.ConflictingOptionException;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.ChangeSetAlreadyInHistoryException;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSet;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmChangeSetList;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmGapFillingChangeSetsReport;
import com.ibm.team.scm.common.internal.rich.rest.dto.ScmGapFillingChangeSetsReportList;
import com.ibm.team.scm.common.rest.IScmRichClientRestService;
import com.ibm.team.scm.common.rest.IScmRichClientRestService.ParmsFillGapChangeSets;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;





public class ListMissingChangeSetsCmd
  extends AbstractSubcommand
  implements IOptionSource
{
  public static final IOptionKey OPT_FILE_ONLY = new OptionKey("summarize-changes");
  public static final PositionalOptionDefinition OPT_SELECTORS = new PositionalOptionDefinition("selectors", 1, -1, "@");
  public static final IOptionKey OPT_WORKSPACE = new OptionKey("workspace");
  

  public ListMissingChangeSetsCmd() {}
  

  public static ChangeSetSyncDTO[] getOriginalChangeSets(IScmRichClientRestService scmService, IScmRichClientRestService.ParmsFillGapChangeSets parms, String repoUri, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    return RepoUtil.findChangeSets(
      Arrays.asList(changeSetItemIds), 
      true, 
      workspaceItemId, 
      "workspace", 
      repoUri, 
      client, 
      config);
  }
  






  public static ChangeSetSyncDTO[] getChangeSets(IScmRichClientRestService scmService, IScmRichClientRestService.ParmsFillGapChangeSets parms, Set<String> originalChangeSetsUUIDs, List<String> csIds, String repoUri, IFilesystemRestClient client, IScmClientConfiguration config)
    throws FileSystemException
  {
    ScmGapFillingChangeSetsReportList reports = null;
    try {
      reports = scmService.getChangeSetsFillingGap(parms);
    } catch (TeamRepositoryException e) {
      if ((e instanceof ChangeSetAlreadyInHistoryException)) {
        IChangeSetHandle changeSetHandle = (IChangeSetHandle)e.getData();
        message = NLS.bind(
          Messages.ListMissingChangeSets_CHANGE_SETS_ALREADY_IN_HISTORY, 
          AliasUtil.alias(changeSetHandle.getItemId(), repoUri, RepoUtil.ItemType.CHANGESET), new Object[0]);
        ChangeSetAlreadyInHistoryException exception = new ChangeSetAlreadyInHistoryException(message);
        exception.setData(changeSetHandle);
        throw StatusHelper.wrap(
          Messages.ListMissingChangeSets_UNABLE_TO_RETRIEVE_FILLING_CHANGE_SETS, 
          exception, 
          new IndentingPrintStream(config.getContext().stderr()), 
          repoUri);
      }
      throw StatusHelper.wrap(
        Messages.ListMissingChangeSets_UNABLE_TO_RETRIEVE_FILLING_CHANGE_SETS, 
        e, 
        new IndentingPrintStream(config.getContext().stderr()), 
        repoUri);
    }
    

    List<ScmGapFillingChangeSetsReport> reports2 = reports.getReports();
    Iterator localIterator; for (String message = reports2.iterator(); message.hasNext(); 
        

        localIterator.hasNext())
    {
      ScmGapFillingChangeSetsReport report = (ScmGapFillingChangeSetsReport)message.next();
      
      List<ScmChangeSet> changeSets = report.getChanges().getChangeSets();
      localIterator = changeSets.iterator(); continue;ScmChangeSet changeSet = (ScmChangeSet)localIterator.next();
      itemId = changeSet.getItemId();
      if (!originalChangeSetsUUIDs.contains(itemId)) {
        csIds.add(itemId);
      }
    }
    

    ChangeSetSyncDTO[] csDTOs = RepoUtil.findChangeSets(
      csIds, 
      true, 
      workspaceItemId, 
      "workspace", 
      repoUri, 
      client, 
      config);
    

    Map<String, ChangeSetSyncDTO> allDtos = new HashMap(csDTOs.length);
    int index = 0;
    boolean needSorting = false;
    ChangeSetSyncDTO[] arrayOfChangeSetSyncDTO1; String str1 = (arrayOfChangeSetSyncDTO1 = csDTOs).length; for (String itemId = 0; itemId < str1; itemId++) { ChangeSetSyncDTO dto = arrayOfChangeSetSyncDTO1[itemId];
      String changeSetItemId = dto.getChangeSetItemId();
      if (!changeSetItemId.equals(csIds.get(index))) {
        needSorting = true;
      }
      index++;
      allDtos.put(changeSetItemId, dto);
    }
    if (needSorting) {
      index = 0;
      for (String changeSetItemId : csIds) {
        csDTOs[index] = ((ChangeSetSyncDTO)allDtos.get(changeSetItemId));
        index++;
      }
    }
    return csDTOs;
  }
  


  private static JSONObject getFileInfosFromChangeSets(ChangeSetSyncDTO[] changeSets, ChangeSetSyncDTO[] originalChangeSets)
  {
    Set<String> originalPaths = new HashSet();
    List<ChangeSyncDTO> changes2; for (ChangeSetSyncDTO dto : originalChangeSets)
    {
      changes = dto.getChanges();
      Iterator localIterator2; for (Iterator localIterator1 = changes.iterator(); localIterator1.hasNext(); 
          

          localIterator2.hasNext())
      {
        ChangeFolderSyncDTO dto2 = (ChangeFolderSyncDTO)localIterator1.next();
        
        changes2 = dto2.getChanges();
        localIterator2 = changes2.iterator(); continue;ChangeSyncDTO dto3 = (ChangeSyncDTO)localIterator2.next();
        originalPaths.add(standardizeDisplayPath(dto3.getPathHint(), 
          dto3.getVersionableItemType().equals("folder")));
      }
    }
    

    Map<String, JSONObject> infos = new HashMap();
    List<ChangeFolderSyncDTO> changes = changeSets;int k = changeSets.length; for (??? = 0; ??? < k; ???++) { ChangeSetSyncDTO dto = changes[???];
      
      List<ChangeFolderSyncDTO> changes = dto.getChanges();
      Iterator localIterator3; for (changes2 = changes.iterator(); changes2.hasNext(); 
          

          localIterator3.hasNext())
      {
        ChangeFolderSyncDTO dto2 = (ChangeFolderSyncDTO)changes2.next();
        
        List<ChangeSyncDTO> changes2 = dto2.getChanges();
        localIterator3 = changes2.iterator(); continue;ChangeSyncDTO dto3 = (ChangeSyncDTO)localIterator3.next();
        String path = standardizeDisplayPath(dto3.getPathHint(), 
          dto3.getVersionableItemType().equals("folder"));
        
        if (!originalPaths.contains(path)) {
          JSONObject fileInfo = (JSONObject)infos.get(path);
          if (fileInfo == null) {
            fileInfo = newFileInfo(path, dto3.getVersionableItemId());
            infos.put(path, fileInfo);
          }
          addChangeSetId(fileInfo, dto.getChangeSetItemId());
        }
      }
    }
    
    JSONArray result = new JSONArray();
    result.addAll(infos.values());
    JSONObject files = new JSONObject();
    files.put("files", result);
    return files;
  }
  
  private static void addChangeSetId(JSONObject fileInfo, String changesetId) {
    ((JSONArray)fileInfo.get("ids")).add(changesetId);
  }
  
  private static JSONObject newFileInfo(String fullPath, String fileItemId) {
    JSONObject fileInfo = new JSONObject();
    fileInfo.put("id", fileItemId);
    fileInfo.put("path", fullPath);
    fileInfo.put("ids", new JSONArray());
    return fileInfo;
  }
  
  private static String standardizeDisplayPath(String path, boolean isFolder) {
    IPath newpath = new Path(path);
    
    if (!newpath.isAbsolute()) {
      newpath = new Path(File.separator).append(newpath);
    }
    
    if ((isFolder) && (!newpath.hasTrailingSeparator())) {
      newpath = newpath.addTrailingSeparator();
    }
    
    path = newpath.toOSString();
    
    return path;
  }
  




  private ITeamRepository generateParms(IScmRichClientRestService.ParmsFillGapChangeSets parms, ICommandLine cli, IFilesystemRestClient client, IScmClientConfiguration config, Set<String> originalChangeSetsIds)
    throws FileSystemException
  {
    IScmCommandLineArgument wsSelector = ScmCommandLineArgument.create(
      cli.getOptionValue(OPT_WORKSPACE, null), config);
    


    ParmsWorkspace ws = null;
    ITeamRepository repo = null;
    if (wsSelector != null) {
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] {
        RepoUtil.ItemType.WORKSPACE, RepoUtil.ItemType.STREAM });
      repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(
        wsSelector.getItemSelector(), 
        true, 
        true, 
        repo, 
        config);
      ws = new ParmsWorkspace(repo.getRepositoryURI(), wsFound.getItemId().getUuidValue());
    } else {
      repo = RepoUtil.loginUrlArgAncestor(config, client, null);
      ws = RepoUtil.findWorkspaceInSandbox(null, repo.getId(), client, config);
    }
    
    workspaceItemId = workspaceItemId;
    

    List<IScmCommandLineArgument> argSelectors = ScmCommandLineArgument.createList(cli.getOptionValues(OPT_SELECTORS), config);
    List<String> selectors = RepoUtil.getSelectors(argSelectors);
    
    SubcommandUtil.validateArgument(argSelectors, RepoUtil.ItemType.CHANGESET);
    
    RepoUtil.validateItemRepos(RepoUtil.ItemType.CHANGESET, argSelectors, repo, config);
    
    for (String selector : selectors) {
      IUuidAliasRegistry.IUuidAlias uuid = RepoUtil.lookupUuidAndAlias(selector);
      String uuid2 = uuid.getUuid().getUuidValue();
      originalChangeSetsIds.add(uuid2);
    }
    changeSetItemIds = ((String[])originalChangeSetsIds.toArray(new String[selectors.size()]));
    return repo;
  }
  
  public Options getOptions() throws ConflictingOptionException {
    Options options = new Options(false, true);
    SubcommandUtil.addRepoLocationToOptions(options);
    
    options
      .addOption(CommonOptions.OPT_JSON, Messages.Common_JSON_OUTPUT)
      .addOption(CommonOptions.OPT_VERBOSE, Messages.ListMissingChangeSetsCmd_VERBOSE_HELP)
      .addOption(
      new ContinuousGroup()
      .addOption(
      OPT_WORKSPACE, 
      "w", 
      "workspace", 
      Messages.ListMissingChangeSetsCmd_Option_WORKSPACE, 
      1, 
      false)
      .addOption(
      OPT_FILE_ONLY, 
      "s", 
      "summarize-changes", 
      Messages.ListMissingChangeSetsCmd_SHOW_FILES_ONLY_OUTPUT, 
      0, 
      false))
      .addOption(OPT_SELECTORS, Messages.ListMissingChangeSetsCmd_OPT_SELECTOR_HELP);
    return options;
  }
  
  public void run() throws FileSystemException {
    ICommandLine cli = config.getSubcommandCommandLine();
    
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    
    IScmRichClientRestService.ParmsFillGapChangeSets parms = new IScmRichClientRestService.ParmsFillGapChangeSets();
    Set<String> originalChangeSetsIds = new HashSet();
    ITeamRepository repo = generateParms(parms, cli, client, config, originalChangeSetsIds);
    
    IScmRichClientRestService scmService = (IScmRichClientRestService)((IClientLibraryContext)repo)
      .getServiceInterface(IScmRichClientRestService.class);
    List<String> csIds = new ArrayList();
    String repositoryURI = repo.getRepositoryURI();
    ChangeSetSyncDTO[] changeSets = getChangeSets(
      scmService, 
      parms, 
      originalChangeSetsIds, 
      csIds, 
      repositoryURI, 
      client, 
      config);
    
    if (changeSets == null) {
      return;
    }
    
    if ((csIds.size() == 0) && (!config.isJSONEnabled())) {
      out.println(Messages.ListMissingChangeSetsCmd_NO_CS_FOUND);
    }
    

    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(4);
    if (cli.hasOption(CommonOptions.OPT_VERBOSE)) {
      options.enablePrinter(6);
      options.enablePrinter(14);
      options.enablePrinter(26);
    }
    if (!cli.hasOption(OPT_FILE_ONLY)) {
      if (!config.isJSONEnabled()) {
        out.println(Messages.ListMissingChangeSetsCmd_FillingChangesets);
      }
      Arrays.sort(changeSets, new Comparator()
      {
        public int compare(ChangeSetSyncDTO dto1, ChangeSetSyncDTO dto2)
        {
          return dto1.getComponentItemId().compareTo(dto2.getComponentItemId());
        }
      });
      PendingChangesUtil.printChangeSets(null, Arrays.asList(changeSets), new ChangeSetStateFactory(), options, out, client, config);
    }
    else {
      ChangeSetSyncDTO[] originalChangeSets = getOriginalChangeSets(
        scmService, 
        parms, 
        repositoryURI, 
        client, 
        config);
      JSONObject fileInfo = getFileInfosFromChangeSets(changeSets, originalChangeSets);
      JSONArray fileInfos = (JSONArray)fileInfo.get("files");
      if (fileInfos.size() == 0) {
        if (!config.isJSONEnabled()) {
          out.println(Messages.ListMissingChangeSetsCmd_NoOtherModifiedFiles);
        } else {
          config.getContext().stdout().println(fileInfo);
        }
      } else if (config.isJSONEnabled())
      {
        config.getContext().stdout().println(fileInfo);
      } else {
        out.println(Messages.ListMissingChangeSetsCmd_ModifiedFiles);
        int i = 0; for (int max = fileInfos.size(); i < max; i++) {
          JSONObject info = (JSONObject)fileInfos.get(i);
          StringBuilder builder = new StringBuilder();
          builder
            .append(AliasUtil.alias((String)info.get("id"), repositoryURI, RepoUtil.ItemType.VERSIONABLE))
            .append(' ')
            .append(info.get("path"))
            .append("  (")
            .append(Messages.ListMissingChangeSetsCmd_ChangeSetsHeader);
          JSONArray allChangeSetIds = (JSONArray)info.get("ids");
          int index = 0; for (int max2 = allChangeSetIds.size(); index < max2; index++) {
            if (index > 0) {
              builder.append(' ');
            }
            String changeSetId = (String)allChangeSetIds.get(index);
            builder.append(
              AliasUtil.aliasNoParen(
              ((ScmClientConfiguration)CommandLineCore.getConfig()).getAliasConfig(), 
              UUID.valueOf(changeSetId), 
              repositoryURI, 
              RepoUtil.ItemType.CHANGESET));
          }
          builder.append(')');
          out.indent().println(builder.toString());
        }
      }
    }
  }
}
