# thermostat-control-android
Android app for controlling a thermostat from http://radiothermostat.com

## Introduction
This app controls a thermostat from [radiothermostat.com](http://radiothermostat.com), which are relatively cheap
and sold online and in home improvement stores. They have wifi, and while the intended use is for you to use
the manufacturer's cloud service for managing your thermostat, there is a [REST API](https://www.nova-labs.org/wiki/_media/projects/radio_thermostat_api_client/rtcoawifiapiv1_3.pdf)
to give you access to all the thermostat's features.

## Security
The REST API has no security at all. You can hide it behind an Apache proxy, adding Basic HTTP
authentication and SSL with something like:

```
<Location /thermostat>
  AuthType Basic
  AuthName "Restricted content"
  AuthUserFile /var/www/.htpasswd.thermostat
  Require valid-user
  ProxyPass http://thermostat.example.com/tstat
</Location>
```

This will protect the REST API endpoint, important if you are exposing this to the Internet. The API will
still be accessible with no authentication from the local network, though.
