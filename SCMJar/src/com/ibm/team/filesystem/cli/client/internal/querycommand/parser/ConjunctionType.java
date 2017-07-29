package com.ibm.team.filesystem.cli.client.internal.querycommand.parser;

import com.ibm.team.scm.common.IGenericQueryNode;
import com.ibm.team.scm.common.IGenericQueryNode.Factory;
import com.ibm.team.scm.common.dto.QueryOperations;
import com.ibm.team.scm.common.internal.query.parser.generated.QueryParser.ConjunctionExprContext;
import org.antlr.v4.runtime.tree.TerminalNode;










 enum ConjunctionType
{
  And, 
  






  Or;
  






  private static IGenericQueryNode makeQuery(String operation, IGenericQueryNode left, IGenericQueryNode right)
  {
    IGenericQueryNode result = IGenericQueryNode.FACTORY.newInstance(operation);
    result.addChild(left);
    result.addChild(right);
    return result;
  }
  
  static ConjunctionType getConjunctionType(QueryParser.ConjunctionExprContext ctx) {
    TerminalNode orNode = ctx.OR();
    TerminalNode andNode = ctx.AND();
    if (orNode != null) {
      return Or;
    }
    if (andNode != null) {
      return And;
    }
    throw new IllegalStateException("Failed to determine conjunction type");
  }
  
  public abstract IGenericQueryNode create(IGenericQueryNode paramIGenericQueryNode1, IGenericQueryNode paramIGenericQueryNode2);
}
