
Configuration
=============

The configuration file is in
``web-view/resources/configurations/configuration.xml``. This section
describes the configuration parameters in this file.

========================
Configuration Parameters
========================

------
daemon
------

If ``true``, then Kepler does not launch GUI editor but waits for REST and websocket requests.
Otherwise (``false``), Kepler starts normally.

----
port
----

Port to listen on for requsts.

-----------
startServer
-----------

``true`` or ``false``: if WebView should be started.


===================
Default config file
===================
.. literalinclude:: ../resources/configurations/configuration.xml
   :language: xml
   :linenos:
