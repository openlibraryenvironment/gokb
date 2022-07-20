
The old grails `~/.grails` config mechanism is deprecated. Override defaults in your local config by
creating a file called something like `/home/ianibbo/localconfig/gokbdev.yaml`


SpringBoot allows us to add a local config file when running with `grails run-app`. You can create

`application-development.yaml`

In -this- directory and it will be used to override any properties. For example, the following config changes the Elasticsearch cluster name

```
gokb:
  es:
    cluster: kbplusg3
```

The application expects a running instance of [this languages-microservice](https://github.com/hbz/languages-microservice/) on `localhost:8070`. Please feel free to configure another address.

`application-development.yml` is ignored in git configuration so as not to pollute other dev envs.
