package com.ibm.team.filesystem.cli.client.internal.changeset;

import com.ibm.team.filesystem.cli.client.internal.Messages;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.INamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.IPositionalOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.NamedOptionDefinition;
import com.ibm.team.rtc.cli.infrastructure.internal.parser.PositionalOptionDefinition;












public abstract class ChangesetCommonOptions
{
  public static final String OPT_CHANGESET_HELP = Messages.ChangesetCommentCmdOptions_2;
  


  public static final IPositionalOptionDefinition OPT_CHANGESET = new PositionalOptionDefinition(Messages.ChangesetCommentCmdOptions_0, 1, 1, "@");
  

  public static final IPositionalOptionDefinition OPT_CHANGESETS = new PositionalOptionDefinition(Messages.ChangesetCommentCmdOptions_0, 1, 
    -1, "@");
  
  public static final INamedOptionDefinition OPT_WORKSPACE_NAME = new NamedOptionDefinition(
    "w", "workspace", 1, "@");
  
  public ChangesetCommonOptions() {}
}
