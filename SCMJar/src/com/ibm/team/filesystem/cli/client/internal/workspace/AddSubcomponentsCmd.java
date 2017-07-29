package com.ibm.team.filesystem.cli.client.internal.workspace;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.filesystem.cli.core.util.AliasUtil;
import com.ibm.team.filesystem.cli.core.util.CLIFileSystemClientException;
import com.ibm.team.filesystem.cli.core.util.RepoUtil.ItemType;
import com.ibm.team.filesystem.cli.core.util.StatusHelper;
import com.ibm.team.filesystem.client.rest.parameters.ParmsComponentHierarchyChange;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.repository.common.util.NLS;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import java.util.List;
import java.util.Map;














public class AddSubcomponentsCmd
  extends UpdateSubcomponentsCmd
{
  public AddSubcomponentsCmd()
  {
    super(Messages.AddSubcomponentsCmd_PARENT_COMPONENT_HELP_TEXT, Messages.AddSubcomponentsCmd_SUBCOMPONENT_HELP_TEXT, Messages.AddSubcomponentsCmd_SUCCESS, Messages.AddSubcomponentsCmd_UNCHANGED);
  }
  
  protected void setSubcomponentsOnParms(ParmsComponentHierarchyChange parms, String[] subcomponentUuids)
  {
    subcomponentsToAddUuids = subcomponentUuids;
  }
  


  public void validateUpdatedSubcomponents(ITeamRepository repo, IComponent parentComponent, List<IComponentHandle> specifiedSubComponents, Map<UUID, IComponentHandle> intersection)
    throws CLIFileSystemClientException
  {
    if (intersection.size() != specifiedSubComponents.size())
    {

      StringBuilder builder = new StringBuilder(Messages.AddSubcomponentsCmd_FAILURE);
      for (IComponentHandle componentHandle : intersection.values()) {
        String name = null;
        if ((componentHandle instanceof IComponent)) {
          IComponent component = (IComponent)componentHandle;
          name = component.getName();
        }
        builder.append("  ");
        builder.append(AliasUtil.selector(name, componentHandle.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT));
        builder.append("\n");
      }
      throw StatusHelper.failure(NLS.bind(builder.toString(), AliasUtil.selector(parentComponent.getName(), parentComponent.getItemId(), repo.getRepositoryURI(), RepoUtil.ItemType.COMPONENT), new Object[0]), null);
    }
  }
}
