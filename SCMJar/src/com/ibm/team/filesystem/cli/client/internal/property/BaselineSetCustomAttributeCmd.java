package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaseline;
import com.ibm.team.filesystem.client.rest.parameters.ParmsBaselineCustomAttributes;
import com.ibm.team.filesystem.client.rest.parameters.ParmsProperty;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.scm.common.IBaseline;
import com.ibm.team.scm.common.IComponent;
import java.io.PrintStream;
import java.util.Map;






public class BaselineSetCustomAttributeCmd
{
  public BaselineSetCustomAttributeCmd() {}
  
  public static void setCustomAttributes(IScmCommandLineArgument blSelector, IScmCommandLineArgument compSelector, Map<String, String> customAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, blSelector);
    

    IComponent comp = null;
    if (compSelector != null) {
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    }
    

    IBaseline bl = RepoUtil.getBaseline(blSelector.getItemSelector(), comp != null ? comp.getItemId().getUuidValue() : null, 
      comp != null ? comp.getName() : null, repo, client, config);
    

    setCustomAttributes(bl, customAttrs, repo, client, config);
    
    config.getContext().stdout().println(Messages.BaselineSetCustomAttributesCmd_PROPERTY_SET_SUCCESS);
  }
  
  private static void setCustomAttributes(IBaseline bl, Map<String, String> customAttrs, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    ParmsBaselineCustomAttributes parms = new ParmsBaselineCustomAttributes();
    baseline = new ParmsBaseline();
    baseline.baselineItemId = bl.getItemId().getUuidValue();
    baseline.repositoryUrl = repo.getRepositoryURI();
    
    ParmsProperty[] properties = new ParmsProperty[customAttrs.size()];
    int i = 0;
    for (String key : customAttrs.keySet()) {
      ParmsProperty prop = new ParmsProperty(key, (String)customAttrs.get(key));
      properties[(i++)] = prop;
    }
    setCustomAttrs = properties;
    

    client.postSetBaselineCustomAttributes(parms, null);
  }
}
