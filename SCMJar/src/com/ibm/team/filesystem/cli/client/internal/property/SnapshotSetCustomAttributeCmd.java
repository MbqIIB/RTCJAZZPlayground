package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSet;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineSetCustomAttributes;
import com.ibm.team.filesystem.client.rest.parameters.ParmsProperty;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IWorkspace;
import java.io.PrintStream;
import java.util.Map;





public class SnapshotSetCustomAttributeCmd
{
  IFilesystemRestClient client;
  
  public SnapshotSetCustomAttributeCmd() {}
  
  public static void setProperties(IScmCommandLineArgument ssSelector, IScmCommandLineArgument wsSelector, Map<String, String> customAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, ssSelector);
    

    IWorkspace ws = null;
    if (wsSelector != null) {
      ws = RepoUtil.getWorkspace(wsSelector.getItemSelector(), true, true, repo, config);
    }
    

    IBaselineSet ss = RepoUtil.getSnapshot(ws != null ? ws.getItemId().getUuidValue() : null, ssSelector.getItemSelector(), repo, config);
    

    setProperties(ss, customAttrs, repo, client, config);
    
    config.getContext().stdout().println(Messages.SnapshotSetCustomAttributesCmd_PROPERTY_SET_SUCCESS);
  }
  
  private static void setProperties(IBaselineSet ss, Map<String, String> customAttrs, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    ParmsBaselineSetCustomAttributes parms = new ParmsBaselineSetCustomAttributes();
    baselineSet = new ParmsBaselineSet();
    baselineSet.baselineSetItemId = ss.getItemId().getUuidValue();
    baselineSet.repositoryUrl = repo.getRepositoryURI();
    
    ParmsProperty[] properties = new ParmsProperty[customAttrs.size()];
    int i = 0;
    for (String key : customAttrs.keySet()) {
      ParmsProperty prop = new ParmsProperty(key, (String)customAttrs.get(key));
      properties[(i++)] = prop;
    }
    setCustomAttrs = properties;
    

    client.postSetBaselineSetCustomAttributes(parms, null);
  }
}
