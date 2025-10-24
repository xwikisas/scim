# SCIM

SCIM related extensions.

* Project Lead: [Raphaël Jakse](http://www.xwiki.org/xwiki/bin/view/XWiki/rjakse)
* Minimal XWiki version supported: 17.10
* License: LGPL 2.1

# Implementation

* Unfortunately, we can't just extend UserResourceImpl, see AbstractScimResourceImpl for more details.
* And unfortunately, JAX-RS ignores annotations from superclasses if the subclass has annotations itself, so we need
* to repeat the methods until we find a more elegant solution.
