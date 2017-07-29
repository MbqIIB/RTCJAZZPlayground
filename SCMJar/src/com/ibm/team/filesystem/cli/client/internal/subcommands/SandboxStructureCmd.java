package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.internal.ScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.CommonOptions;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.subcommands.IUuidAliasRegistry.IUuidAlias;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.JSONPrintUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil;
import com.ibm.team.filesystem.cli.core.util.PendingChangesUtil.PendingChangesOptions;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsResourceProperties;
import com.ibm.team.filesystem.client.rest.parameters.ParmsWorkspace;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.WorkspaceComponentDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcesDTO;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.json.JSONArray;
import com.ibm.team.repository.common.json.JSONObject;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.core.ISubcommandDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.OptionKey;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.scm.common.IWorkspace;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.util.NLS;





public class SandboxStructureCmd
  extends AbstractSubcommand
{
  public SandboxStructureCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    config.setEnableJSON(cli.hasOption(CommonOptions.OPT_JSON));
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    

    File cfaRoot = SubcommandUtil.findAncestorCFARoot(config.getContext().getCurrentWorkingDirectory());
    if (cfaRoot == null) {
      throw StatusHelper.itemNotFound(NLS.bind(Messages.SandboxStructureCmd_INVALID_SANDBOX, CommonOptions.OPT_DIRECTORY));
    }
    

    JSONObject root = new JSONObject();
    SubcommandUtil.isCorrupt(root, false, config);
    

    IScmCommandLineArgument wsSelector = null;
    if (cli.hasOption(SandboxStructureCmdOpts.OPT_WORKSPACE)) {
      wsSelector = ScmCommandLineArgument.create(cli.getOptionValue(SandboxStructureCmdOpts.OPT_WORKSPACE, null), config);
      SubcommandUtil.validateArgument(wsSelector, new RepoUtil.ItemType[] { RepoUtil.ItemType.WORKSPACE });
    }
    

    IScmCommandLineArgument compSelector = null;
    if (cli.hasOption(SandboxStructureCmdOpts.OPT_COMPONENT)) {
      compSelector = ScmCommandLineArgument.create(cli.getOptionValue(SandboxStructureCmdOpts.OPT_COMPONENT), config);
      SubcommandUtil.validateArgument(compSelector, RepoUtil.ItemType.COMPONENT);
    }
    

    PendingChangesUtil.PendingChangesOptions options = new PendingChangesUtil.PendingChangesOptions();
    options.enablePrinter(17);
    options.enablePrinter(0);
    if (wsSelector != null) {
      ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, wsSelector);
      IWorkspace wsFound = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, false, repo, config);
      options.addFilter(wsFound, 0);
    }
    JSONObject pendingChanges = JSONPrintUtil.jsonizePendingChanges(options, client, config);
    JSONArray activeWsArr = (JSONArray)pendingChanges.get("workspaces");
    JSONArray unreachableWsArr = (JSONArray)pendingChanges.get("unreachable-workspaces");
    

    if ((activeWsArr != null) && (activeWsArr.size() == 0) && (unreachableWsArr != null) && (unreachableWsArr.size() == 0)) {
      throw StatusHelper.misconfiguredLocalFS(Messages.SandboxStructureCmd_LOADED_WS_NOT_FOUND);
    }
    

    IndentingPrintStream out = new IndentingPrintStream(config.getContext().stdout());
    JSONArray jShares = new JSONArray();
    List<IPath> sharePaths = new ArrayList();
    boolean compFound = false;
    
    if (activeWsArr != null) label525:
      for (Object val : activeWsArr) {
        JSONObject ws = (JSONObject)val;
        
        if ((JSONArray)ws.get("out-of-sync") != null) {
          options.setFilesystemOutOfSync(true);
        }
        
        if ((wsSelector == null) || (isWorkspaceInSandbox(wsSelector, ws)))
        {

          WorkspaceComponentDTO comp = null;
          String wsId = (String)ws.get("uuid");
          String wsUrl = (String)ws.get("url");
          if (compSelector != null) {
            ParmsWorkspace wsParms = new ParmsWorkspace(wsUrl, wsId);
            try {
              comp = RepoUtil.getComponent(wsParms, compSelector.getItemSelector(), client, config);
              if (comp != null) {
                compFound = true;
              }
              if (isComponentInWorkspace(comp, ws, client)) {
                break label525;
              }
              
            }
            catch (CLIFileSystemClientException localCLIFileSystemClientException)
            {
              if (!compFound) break label525; }
            continue;
            

            if (!compFound) {
              throw StatusHelper.itemNotFound(NLS.bind(Messages.SandboxStructureCmd_COMP_NOT_FOUND, compSelector.getItemSelector()));
            }
          }
          
          List<ShareDTO> shares = RepoUtil.getSharesInSandbox(wsId, client, config);
          for (ShareDTO share : shares) {
            if ((comp == null) || (comp.getItemId().equals(share.getComponentItemId()))) {
              populateSharePaths(share, sharePaths);
            }
          }
        }
      }
    populateShareJson(jShares, sharePaths, client);
    showResult(root, jShares, cfaRoot.getAbsolutePath(), out);
    

    unreachableWsArr = (JSONArray)pendingChanges.get("unreachable-workspaces");
    if ((unreachableWsArr != null) && (unreachableWsArr.size() > 0)) {
      if (config.isJSONEnabled()) {
        root.put("unreachable-workspaces", unreachableWsArr);
      } else {
        PendingChangesUtil.printUnreacableWorkspaces(unreachableWsArr, out, config);
      }
    }
    

    if ((!config.isJSONEnabled()) && (options.isFilesystemOutOfSync())) {
      ISubcommandDefinition defnTemp = SubcommandUtil.getClassSubCommandDefn(config, LoadCmdLauncher.class);
      config.getContext().stdout().println(
        NLS.bind(
        Messages.CheckInCmd_9, 
        new String[] {
        config.getContext().getAppName(), 
        SubcommandUtil.getExecutionString(defnTemp).toString(), 
        ((OptionKey)LoadCmdOptions.OPT_FORCE).getName(), 
        ((OptionKey)LoadCmdOptions.OPT_RESYNC).getName() }));
    }
    


    if (options.isPartialStatus()) {
      throw StatusHelper.partialStatus();
    }
  }
  
  private void populateSharePaths(ShareDTO share, List<IPath> sharePaths)
  {
    IPath sharePath = new Path(share.getSandboxPath());
    PathDTO sharePathDto = share.getPath();
    for (String segment : sharePathDto.getSegments()) {
      sharePath = sharePath.append(segment);
    }
    sharePaths.add(sharePath);
  }
  
  private void populateShareJson(JSONArray jShares, List<IPath> sharePaths, IFilesystemRestClient client)
  {
    List<String> localPaths = new ArrayList();
    

    for (IPath sharePath : sharePaths) {
      localPaths.add(sharePath.toOSString());
    }
    
    ParmsResourceProperties parms = new ParmsResourceProperties();
    fullResourcePaths = ((String[])localPaths.toArray(new String[localPaths.size()]));
    computeFully = Boolean.valueOf(true);
    try
    {
      ResourcesDTO resources = client.getResourceProperties(parms, null);
      for (ResourcePropertiesDTO properties : resources.getResourceProperties()) {
        String localPath = properties.getFullPath();
        PathDTO remotePathDto = properties.getRemotePath();
        

        if (remotePathDto != null) {
          IPath shareRemotePath = new Path("");
          for (String segment : remotePathDto.getSegments()) {
            shareRemotePath = shareRemotePath.append(segment);
          }
          StringBuilder remotePath = new StringBuilder(shareRemotePath.toOSString());
          if (properties.getShare().getRootVersionableItemType().equals("folder")) {
            remotePath.append('/');
          }
          
          JSONObject remoteJson = new JSONObject();
          JSONObject jShare = new JSONObject();
          JSONObject workspaceJson = new JSONObject();
          JSONObject componentJson = new JSONObject();
          JSONObject pathJson = new JSONObject();
          jShare.put("local", localPath);
          

          pathJson.put("path", remotePath.toString());
          pathJson.put("uuid", properties.getShare().getRootVersionableItemId());
          pathJson.put("type", properties.getShare().getRootVersionableItemType());
          
          workspaceJson.put("name", properties.getShare().getContextName());
          workspaceJson.put("uuid", properties.getShare().getContextItemId());
          
          componentJson.put("name", properties.getShare().getComponentName());
          componentJson.put("uuid", properties.getShare().getComponentItemId());
          
          remoteJson.put("workspace", workspaceJson);
          remoteJson.put("component", componentJson);
          remoteJson.put("path", pathJson);
          
          jShare.put("remote", remoteJson);
          jShares.add(jShare);
        }
      }
    } catch (TeamRepositoryException localTeamRepositoryException) {
      StatusHelper.propertiesUnavailable(Messages.SandboxStructureCmd_COULD_NOT_FETCH_PROPERTIES);
    }
  }
  
  private void showResult(JSONObject root, JSONArray shares, String sandboxRoot, IndentingPrintStream out)
  {
    if (config.isJSONEnabled()) {
      JSONArray array = new JSONArray();
      array.addAll(shares);
      root.put("sandbox", sandboxRoot);
      root.put("shares", array);
      config.getContext().stdout().print(root);
      return;
    }
    

    out.println(Messages.SandboxStructureCmd_SANDBOX + sandboxRoot);
    out = out.indent();
    
    if (shares.size() == 0) {
      out.println(Messages.SandboxStructureCmd_PROJECTS_NOT_FOUND);
      return;
    }
    
    for (Object share : shares) {
      JSONObject result = (JSONObject)share;
      out.println(Messages.SandboxStructureCmd_LOCAL + (String)result.get("local"));
      JSONObject remotePathObj = (JSONObject)result.get("remote");
      String workspace = (String)((JSONObject)remotePathObj.get("workspace")).get("name");
      String component = (String)((JSONObject)remotePathObj.get("component")).get("name");
      String path = (String)((JSONObject)remotePathObj.get("path")).get("path");
      String remotePath = workspace + '/' + component + '/';
      if (!path.equals("/")) {
        remotePath = remotePath + path;
      }
      out.indent().println(Messages.SandboxStructureCmd_REMOTE + remotePath);
    }
  }
  

  private boolean isWorkspaceInSandbox(IScmCommandLineArgument sourceSelector, JSONObject ws)
  {
    IUuidAliasRegistry.IUuidAlias uuidAlias = RepoUtil.lookupUuidAndAlias(sourceSelector.getItemSelector());
    if (uuidAlias != null) {
      String uuid = (String)ws.get("uuid");
      if (uuidAlias.getUuid().toString().equals(uuid)) {
        return true;
      }
    }
    else {
      String name = (String)ws.get("name");
      if (sourceSelector.getItemSelector().equals(name)) {
        return true;
      }
    }
    return false;
  }
  
  private boolean isComponentInWorkspace(WorkspaceComponentDTO comp, JSONObject ws, IFilesystemRestClient client) {
    String wsId = (String)ws.get("uuid");
    Map<String, String> comps = new HashMap();
    try {
      comps = RepoUtil.getComponentsInSandbox(wsId, client, config);
    }
    catch (FileSystemException localFileSystemException) {}
    
    return (comps.size() > 0) && (comps.keySet().contains(comp.getItemId()));
  }
}
