
Install
=======

Prerequisites

* ant 1.82 or newer
* git 
* java 8 or newer
* maven 3.03 or newer
* svn 1.6 or newer

First, install Kepler development trunk from svn::

 mkdir kepler.modules
 cd kepler.modules
 svn co https://code.kepler-project.org/code/kepler/trunk/modules/build-area
 cd build-area
 ant change-to -Dsuite=reporting
 ant compile

WebView is part of the `reporting` suite, so if you install this suite,
you can run WebView. You can also add WebView to another suite by adding
the following to modules.txt::

 web-view https://github.com/Kepler-Project/web-view.git
