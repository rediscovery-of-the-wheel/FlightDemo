/* Generated By:JJTree: Do not edit this line. ASTRelationalGT.java Version 6.1 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.serenegiant.lang.script;

public
class ASTRelationalGT extends SimpleNode {
  public ASTRelationalGT(int id) {
    super(id);
  }

  public ASTRelationalGT(ScriptParser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(ScriptParserVisitor visitor, Object data) {

    return
    visitor.visit(this, data);
  }
}
/* JavaCC - OriginalChecksum=f633b2d04e3c0783b8c410909007c9dc (do not edit this line) */
