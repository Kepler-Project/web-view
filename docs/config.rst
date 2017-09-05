
Configuration
=============

The configuration file is in
``web-view/resources/configurations/configuration.xml``. This section
describes the configuration parameters in this file.

========================
Configuration Parameters
========================

-----------
startServer
-----------

``true`` or ``false``: if WebView should be started.

------
daemon
------

If ``true``, then Kepler does not launch GUI editor but wait for REST and websocket requests.
Otherwise (``false``), Kepler starts normally.

===================
Default config file
===================
.. literalinclude:: ../resources/configurations/configuration.xml
   :language: xml
   :linenos:
