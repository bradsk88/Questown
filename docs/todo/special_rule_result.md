When a job depends fully on its special rules for result generation, it will often
declare "minecraft:air" as its result (in the JSON file). Let's make these files
support a different value for result like:

```
{
 "result": {
    "type": "uses_special_rules"
}
```

When this is used, the output in code will STILL use miencraft:air, but this will
make it easier for readers of the JSON file to understand how the job works.