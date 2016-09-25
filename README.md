# JsonCreator
Intellij idea json creator generator plugin

#Usage
Generates @JsonCreator using "JsonCreator" item in generate dropdown menu.

#Quick start
1. [Download jar](https://github.com/volkov/jsoncreator/releases/download/0.2/jsoncreator.jar)
1. Add plugin to idea (File->Settings->Plugins->Install plugin from disk...)
1. In class open generate menu and select "JsonCreator" 


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
