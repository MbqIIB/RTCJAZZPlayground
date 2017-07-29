package com.ibm.team.filesystem.cli.client.internal.subcommands;

import com.ibm.team.filesystem.cli.client.AbstractSubcommand;
import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.cli.core.util.SubcommandUtil;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILocation;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsRecomputeLocalChanges;
import com.ibm.team.filesystem.client.rest.parameters.ParmsSandboxPaths;
import com.ibm.team.filesystem.common.internal.rest.client.core.PathDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareDTO;
import com.ibm.team.filesystem.common.internal.rest.client.core.ShareableDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.ResourcePropertiesDTO;
import com.ibm.team.filesystem.common.internal.rest.client.resource.SandboxPathsResultDTO;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.ICommandLine;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.util.IndentingPrintStream;
import com.ibm.team.rtc.cli.infrastructure.internal.util.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.osgi.util.NLS;





public class RefreshCmd
  extends AbstractSubcommand
{
  public RefreshCmd() {}
  
  public void run()
    throws FileSystemException
  {
    ICommandLine cli = config.getSubcommandCommandLine();
    IFilesystemRestClient client = SubcommandUtil.setupDaemon(config);
    
    List<String> selectors = cli.getOptions(RefreshCmdOpts.OPT_REFRESH.getId());
    

    List<String> pathSelectors = new ArrayList();
    for (String selector : selectors) {
      ILocation path = SubcommandUtil.makeAbsolutePath(config, selector);
      pathSelectors.add(path.toOSString());
    }
    

    ParmsSandboxPaths parmsPaths = new ParmsSandboxPaths();
    includeNonRegisteredSandboxes = true;
    pathsToResolve = ((String[])pathSelectors.toArray(new String[pathSelectors.size()]));
    
    SandboxPathsResultDTO pathsResult = null;
    try {
      pathsResult = client.getSandboxPaths(parmsPaths, null);
    } catch (TeamRepositoryException e) {
      throw StatusHelper.wrap(Messages.Common_UNABLE_TO_GET_SANDBOX_PATHS, e, 
        new IndentingPrintStream(config.getContext().stderr()), null);
    }
    

    Map<String, Set<String>> sandboxToPath = new HashMap();
    int count = 0;
    Set<String> paths;
    for (ShareableDTO shareables : pathsResult.getPaths()) {
      if ((shareables.getSandboxPath() == null) || (shareables.getSandboxPath().length() == 0)) {
        throw StatusHelper.inappropriateArgument(NLS.bind(Messages.Common_PATH_NOT_SHARED, pathSelectors.get(count)));
      }
      
      paths = (Set)sandboxToPath.get(shareables.getSandboxPath());
      if (paths == null) {
        paths = new HashSet();
        sandboxToPath.put(shareables.getSandboxPath(), paths);
      }
      
      if (shareables.getRelativePath().getSegments().size() == 0) {
        List<ShareDTO> shares = RepoUtil.getSharesInSandbox(null, new PathLocation(shareables.getSandboxPath()), 
          client, config);
        if (shares.size() > 0) {
          for (ShareDTO share : shares) {
            paths.add(StringUtil.createPathString(share.getPath().getSegments()));
          }
        }
      } else {
        ResourcePropertiesDTO resource = RepoUtil.getResourceProperties((String)pathSelectors.get(count), client, config);
        paths.add(StringUtil.createPathString(resource.getPath().getRelativePath().getSegments()));
      }
      count++;
    }
    

    SubcommandUtil.registerSandboxes((String[])sandboxToPath.keySet().toArray(
      new String[sandboxToPath.keySet().size()]), client, config);
    

    ParmsRecomputeLocalChanges parm = new ParmsRecomputeLocalChanges();
    
    for (String sandbox : sandboxToPath.keySet()) {
      sandboxPath = sandbox;
      paths = new String[((Set)sandboxToPath.get(sandbox)).size()];
      ((Set)sandboxToPath.get(sandbox)).toArray(paths);
      try
      {
        client.getRecomputeLocalChanges(parm, null);
      }
      catch (TeamRepositoryException e) {
        throw StatusHelper.wrap(NLS.bind(Messages.RefreshCmd_REFRESH_FAILED, sandbox), e, new IndentingPrintStream(config.getContext().stderr()));
      }
    }
  }
}
