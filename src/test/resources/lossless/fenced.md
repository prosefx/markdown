# Fenced blocks

Top level, no info string:

```
plain code
```

With an info string:

```java title="x"
int x = 1;
    y(); // indented, "quoted", ` tick
```

Markdown-looking lines are code:

```
# not a heading
- not an item
>> not a comment
---
> not a quote
| a | b |
```

Three backticks inside four:

````md
```java
int x;
```
````

A long line stays one line:

```
word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word word
```

- A space indented item
  ```sh
  ls -la
  ```
- The instruction, verbatim (tab indented, as in a PRP)
	```
	make it open
		indented more

	# not a heading
	```
- Context
	- nested
		```
		deep
		```
	- sibling

  After the blocks, a paragraph.
