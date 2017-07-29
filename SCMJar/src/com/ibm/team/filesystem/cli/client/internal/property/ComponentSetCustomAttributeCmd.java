package com.ibm.team.filesystem.cli.client.internal.property;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.subcommands.IScmClientConfiguration;
import com.ibm.team.filesystem.cli.core.subcommands.IScmCommandLineArgument;
import com.ibm.team.filesystem.cli.core.util.RepoUtil;
import com.ibm.team.filesystem.client.rest.IFilesystemRestClient;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentCustomAttributes;
import com.ibm.team.filesystem.client.rest.parameters.ParmsProperty;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.rtc.cli.infrastructure.internal.core.IExecutionContext;
import com.ibm.team.scm.common.IComponent;
import java.io.PrintStream;
import java.util.Map;






public class ComponentSetCustomAttributeCmd
{
  public ComponentSetCustomAttributeCmd() {}
  
  public static void setCustomAttributes(IScmCommandLineArgument compSelector, Map<String, String> customAttrs, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    ITeamRepository repo = RepoUtil.loginUrlArgAncestor(config, client, compSelector);
    

    IComponent comp = null;
    if (compSelector != null) {
      comp = RepoUtil.getComponent(compSelector.getItemSelector(), repo, config);
    }
    
    setCustomAttributes(comp, customAttrs, repo, client, config);
    
    config.getContext().stdout().println(Messages.ComponentSetCustomAttributesCmd_PROPERTY_SET_SUCCESS);
  }
  
  private static void setCustomAttributes(IComponent comp, Map<String, String> customAttrs, ITeamRepository repo, IFilesystemRestClient client, IScmClientConfiguration config)
    throws TeamRepositoryException
  {
    ParmsComponentCustomAttributes parms = new ParmsComponentCustomAttributes();
    componentUuid = comp.getItemId().getUuidValue();
    repositoryUrl = repo.getRepositoryURI();
    
    ParmsProperty[] properties = new ParmsProperty[customAttrs.size()];
    int i = 0;
    for (String key : customAttrs.keySet()) {
      ParmsProperty prop = new ParmsProperty(key, (String)customAttrs.get(key));
      properties[(i++)] = prop;
    }
    setCustomAttrs = properties;
    

    client.postSetComponentCustomAttributes(parms, null);
  }
}
