# JsonCreator
Intellij idea json creator generator plugin

#Usage
Generates @JsonCreator using "JsonCreator" item in generate dropdown menu.

Generated code sample:
```
  @JsonCreator
  public SomeJson(
      @JsonProperty("name") final String name,
      @JsonProperty("count") final int count)
  {
    this.name = name;
    this.count = count;
  }
```

# Downloads

[Latest plugin jar](https://github.com/volkov/jsoncreator/releases/download/0.1/jsoncreator.jar)
