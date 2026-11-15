# Appkin: context for AI assistants

Appkin is an app that turns a JSON configuration into a native app. You write the configuration; the app validates it against the v3 schema and shows it. This document is the complete contract: use only what it describes.

## Output contract

Return the configuration as one valid JSON object, with no comments, Markdown fences or
prose inside or around it. When you can create a file, make it a plain-text JSON file named
after the app, such as `Bucharest in 3 Days.appkin.json`: never a PDF, a document, a canvas
or an image of the JSON, which Appkin can't open. A short note beside it may say what you
left out and why.

Every new configuration must begin with these root fields:

```json
{
  "schemaVersion": 3,
  "name": "Application name",
  "version": "1.0.0",
  "mainView": {
    "type": "layout",
    "views": []
  }
}
```

The optional root fields are `author`, `icon`, `changelog`, `debugMode`, `views`, and
`actions`.

## Generation rules

1. Use only the properties this document describes, with lowercase enum values exactly
   as written. The schema rejects unknown properties.
2. Make `mainView` an inline view unless it intentionally references a reusable root
   view. Put reusable screens in root `views` and reusable behaviour in root `actions`.
3. Reference a reusable view or action with `{ "ref": "id" }`, never `{ "id": "..." }`
   alone. Give every referenced definition a unique, non-empty `id`.
4. Give every action definition a complete `event`.
5. Trace the data at every node: a list's `itemView` gets one item, a `rest` event's
   `thenDo` gets the response, a form's action gets the submitted values, and a `show`
   event's view gets the current data.
6. Use `mapping` for a path to one value or array; use `${path}` to build text or a URL
   from values. Match the real data exactly, including case and nesting.
7. Every list needs an `itemView` and one source of items: `mapping` to an array of
   objects (in `data`, or in the `initData` response), or `stored` for objects saved on
   the phone (no `mapping`).
8. Use HTTPS. Never write a real API key, token, password or other secret into the
   JSON: write a placeholder of two underscores, an uppercase name and two underscores,
   such as `?api_key=__EXAMPLE_API_KEY__` or
   `"headers": { "Authorization": "Bearer __EXAMPLE_TOKEN__" }`. Appkin asks the user for
   each value when the app opens, stores it encrypted and fills it into every request.
   Use one name per key.
9. Prefer a small working configuration over speculative or undocumented fields.
10. Before answering, check the JSON syntax and that every reference resolves to exactly
    one definition.
11. When the app should show pictures, give it real image addresses: open each one if you
    can, and keep those that show the picture. Never build one from a name (a place's, a
    file's). If you can't open web pages, use only addresses you are confident of, rather
    than leaving the pictures out: Appkin checks every image when the app opens and sends
    back the ones that don't load, for you to replace.

## Data-flow checklist

Before writing JSON, identify:

- The fixed data, if any, and the data saved on the phone (which collections)
- The exact shape of every REST response
- Which object is the current data when each action runs
- Which array, or which collection, each list shows
- Which values each form submits
- Which object each opened view receives

Then build the configuration in this order: reusable views, reusable actions and event
chains, the main view, references, and a final syntax and schema check.

## Supported language

Views:

- `layout`
- `text`
- `button`
- `image`
- `list`
- `form`
- `check` (a tick box for a true/false field; ticking it runs its action at once, usually `update`)

Events:

- `show`
- `rest`
- `alert`
- `calendar` (opens the phone's calendar app on a new event, for the user to save)
- `save` (saves the current data, such as a form's values, in a collection on the phone)
- `update` (changes a saved object, by default the one with the current data's `id`)
- `delete` (removes a saved object, or all that match `where`; put it behind an alert to ask first)
- `back` (closes the current screen, such as after a form saves an edit)
- `copy` (copies a text to the clipboard, to paste in a chat)
- `open` (hands a `https:`, `http:`, `geo:`, `tel:` or `mailto:` address to the browser,
  maps, the dialer or email; nothing else opens)
- `share` (opens the share sheet on a text)

Other enums:

- Orientations: `vertical`, `horizontal` (and `grid`, for lists only)
- Actions: `click`, `longclick`
- HTTP methods: `get`, `post`, `put`, `delete`
- Text styles: `regular`, `italic`, `bold`
- Text formats: `plain`, `html`, `markdown`
- Text inputs: `plain`, `numeric`, `email`, `password`, `url`
- Form elements: `text`, `selector`, `date`, `time`, `checkbox`, `hidden`, `photos` (pick photos from the gallery, kept on the phone), `tags` (labels a stored list can filter on)

Do not invent support for PATCH, JavaScript expressions, conditions other than
`visibleIf`, databases beyond `save`, `update`, `delete` and `stored`, arbitrary native
components, or undocumented style properties.

## Common mistakes

### Text, buttons and images

- A button shows its `title` (or the value its `mapping` selects); its `label` is not
  displayed.
- A text view shows its `label` as a heading, then the `mapping` value or `title` under it
  as a quieter line. Use `textStyle` and `textSize` for emphasis, and `labelStyle` to style
  the label alone (`"labelStyle": "bold"`). For a value holding HTML, add
  `"format": "html"`; for longer text you write yourself (a description, tips),
  `"format": "markdown"` allows headings, **bold**, *italics*, lists and links. A web address
  written in a text view becomes a link.
- An image's `"scale": "fit"` shows the whole picture instead of cropping it.
- Sizes are numbers (dp and sp), not strings: `"size": { "height": 200 }`.
- Give every app an `icon`: one emoji that fits it, such as `"🏍️"` for a motorcycle guide.
  It is the app's icon on the home page and the phone's home screen.

### Lists and data

- A list's fixed items go in `data` on the list or a parent; a REST list uses `initData`.
  For an API with pages (a `skip`, `offset` or `page` parameter, or a "next" link), add
  `"nextPage": { "parameter": "skip", "step": 40 }` (the page size) or
  `"nextPage": { "urlFrom": "info.next" }` to `initData`. `"orientation": "grid"` with
  `"columns": 2` shows items side by side; `emptyText` says what an empty list means.
- Inside `itemView`, and in views opened from an item, mappings and `${...}` refer to that
  one item's fields, not to the whole response.
- Paths reach array items by index: `meals.0.strMeal`. When a response wraps one object in
  an array, open its view with a `show` event whose `mapping` is `"meals.0"`.
- `mapping` is a plain path such as `owner.name`; `${owner.name}` only builds text. A
  placeholder always starts with `$`: `${title}`, never `{title}`.
- Data is often incomplete. A text view with nothing to show isn't drawn, label included,
  and a missing `${...}` becomes empty text. To hide another view, such as a button that
  needs a field, use `"visibleIf": "field"` (shown when it has a value) or
  `"visibleIf": "!field"`. It is a path, never a comparison.
- An event's `data`, like an action's, replaces the current data it runs with; a form's
  submitted values still come first.

### Forms

- Form fields are `text`, `selector`, `date`, `time`, `checkbox`, `hidden`, `photos` and
  `tags`; there is no `input`, `dropdown` or `switch`. Dates are `dd/MM/yyyy`, times
  `HH:mm`. A selector option is a string or `{ "label": "Any type", "value": "" }`.
- To send something from the screen with a form, such as an item's id, use a `hidden`
  field: `{ "type": "hidden", "mapping": "id", "value": "${id}" }`.

### Saved on the phone

- For a list the user ticks off or edits, such as places to visit or things to pack, use a
  `stored` list with `initialItems`: the full objects, each with every field its views
  show and a `key`, plus `"unique": "key"`. The list saves them itself the first time, and
  later versions' new items are added without undoing ticks; never add a button that
  saves them. Tick with a `check` whose action is an `update` of that collection.
- Order a stored list with `"sort": [{ "by": "visited" }, { "by": "order" }]`: `false`
  comes before `true`. `order` lists values to put first, always as text
  (`["High", "Low"]`, `["false", "true"]`); `"descending": true` turns it round.

### Links and requests

- In a URL, Appkin percent-encodes what each `${...}` inserts: write `?q=${q}`, never
  encode a placeholder yourself. Fixed text is sent as written, so encode it
  (`type=Arms%20and%20Armor`). A query parameter that is only a placeholder
  (`&type=${type}`) is left out when the value is empty.
- Never invent or guess an address (images, `rest` URLs, web pages) or its parts (folders,
  file names, IDs, sizes): it almost never loads, or shows the wrong thing. Use addresses
  the user gives, fields of a REST response, or ones you opened and saw work (for images,
  see rule 11).
- For a request that can fail in a way the user should understand, add `onError` (its
  data has `error.message`).
- For a button that opens a page, a map, the dialer or email, use an `open` event.

### Structure

- An inline action needs both `id` and `event`; to reuse one, define it in the root
  `actions` and reference it with `{ "ref": "id" }`.
- A screen opened with `show` already has Back: don't add Back buttons or alerts that
  explain it. Its `title` names the screen.
- JSON has no comments and no trailing commas.

## Reference

Generated from the v3 JSON Schema. Unknown properties are rejected. **Required** properties are marked.

### Root object

Machine-readable contract for an Appkin v3 application. schemaVersion is optional for backward compatibility; new configurations should set it to 3.

- `schemaVersion`: always `3`. Configuration-language version. Optional only for compatibility with configurations created before schema versioning was introduced
- `name` **(required)**: text (not empty)
- `author`: text
- `debugMode`: `true` or `false`
- `version` **(required)**: text (not empty)
- `icon`: text. One emoji for the app's icon on the home page and the phone's home screen, such as "🏍️". Without it, the icon is the name's first letter
- `changelog`: text
- `mainView` **(required)**: a view (or `{ "ref": "id" }`)
- `views`: an array; each item is a view. Reusable view definitions. References are not valid in this registry
- `actions`: an array; each item is an action definition. Reusable action definitions. References are not valid in this registry

### Views

Every view has a `type`. Where a view is expected, `{ "ref": "id" }` may reference a view defined in the root `views` instead.

#### `layout`

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`)
- `mapping`: text
- `data`: an object with any data
- `visibleIf`: text
- `orientation`: one of `"horizontal"`, `"vertical"`. How the child views are arranged; vertical by default
- `views` **(required)**: an array; each item is a view (or `{ "ref": "id" }`). The child views, in order

#### `text`

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`)
- `mapping`: text. Path of a value in the current data to show as the text, such as owner.name
- `data`: an object with any data
- `visibleIf`: text
- `textSize`: a number (at least 0). Font size in sp
- `size`: a size, `{ "width": number, "height": number }` in dp
- `textStyle`: one of `"regular"`, `"italic"`, `"bold"`
- `title`: text. The text, when no mapping value is shown
- `label`: text. First line of the text view, shown above the text
- `labelStyle`: one of `"regular"`, `"italic"`, `"bold"`. The label's own style, so a bold heading needn't make its text bold; without it the label looks like the text
- `format`: one of `"plain"`, `"html"`, `"markdown"`. plain (default); html: simple HTML in the text (`<b>`, `<i>`, `<em>`, `<strong>`, `<u>`, `<br>`, `<p>`, `<a href>`), such as a museum's descriptions; or markdown: headings (`#`), `**bold**`, `*italics*`, lists (`-` or `1.`), quotes (`>`), `` `code` `` and `[links](https://...)`, such as a description you write
- `showWhenEmpty`: `true` or `false`. Show the label even when the mapping or title has nothing to show. By default such a text view isn't drawn at all

#### `button`

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`). What a tap does
- `mapping`: text. Path of a value in the current data to use as the button's text
- `data`: an object with any data
- `visibleIf`: text
- `textSize`: a number (at least 0)
- `size`: a size, `{ "width": number, "height": number }` in dp
- `textStyle`: one of `"regular"`, `"italic"`, `"bold"`
- `title`: text. The button's text
- `label`: text. Not displayed on buttons: use title. On a button with only an icon, what screen readers say, such as "Delete ${title}"
- `icon`: one of `"add"`, `"check"`, `"close"`, `"copy"`, `"delete"`, `"edit"`, `"email"`, `"favorite"`, `"info"`, `"open"`, `"phone"`, `"place"`, `"refresh"`, `"search"`, `"settings"`, `"share"`, `"star"`. An icon from the phone's standard set, before the title; without a title the button is just the icon, in its own small size

#### `image`

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`)
- `mapping`: text. Path of an image address in the current data
- `data`: an object with any data
- `visibleIf`: text
- `url`: text. Image address; may use ${field} placeholders
- `size`: a size, `{ "width": number, "height": number }` in dp
- `scale`: one of `"crop"`, `"fit"`. crop (default): the picture fills its space and its edges may be cut; fit: the whole picture shows, inside its space

#### `list`

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`). What tapping an item does; its event receives that item
- `swipeToStart`: an action (or `{ "ref": "id" }`). What swiping an item towards the start (left) does; its event receives that item, such as an alert asking before a delete. The item slides back unless the action removes it
- `swipeToStartIcon`: one of `"add"`, `"check"`, `"close"`, `"copy"`, `"delete"`, `"edit"`, `"email"`, `"favorite"`, `"info"`, `"open"`, `"phone"`, `"place"`, `"refresh"`, `"search"`, `"settings"`, `"share"`, `"star"`. The icon under an item swiped towards the start; delete by default
- `swipeToEnd`: an action (or `{ "ref": "id" }`). What swiping an item towards the end (right) does; its event receives that item, such as an update marking it done
- `swipeToEndIcon`: one of `"add"`, `"check"`, `"close"`, `"copy"`, `"delete"`, `"edit"`, `"email"`, `"favorite"`, `"info"`, `"open"`, `"phone"`, `"place"`, `"refresh"`, `"search"`, `"settings"`, `"share"`, `"star"`. The icon under an item swiped towards the end; check by default
- `mapping`: text. Path of the array of objects to show, in data or in the initData response
- `data`: an object with any data
- `visibleIf`: text
- `itemView` **(required)**: a view (or `{ "ref": "id" }`). The view repeated for each item; its mappings and placeholders refer to that item
- `initData`: a REST request, `{ "url": ..., "method": "get" | "post" }`. A REST request whose JSON response is the list's data
- `stored`: an object. Shows objects the app saved on the phone with a save event, instead of initData or data; mapping is not used
- `orientation`: one of `"horizontal"`, `"vertical"`, `"grid"`. vertical (default): one item per row; horizontal: a row that scrolls sideways; grid: items side by side in rows of columns
- `columns`: a whole number (at least 1). Items per row of a grid; 2 by default
- `emptyText`: text. What the list says when it has no items, instead of No data available; may use ${field} placeholders

#### `form`

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`). Runs on submit, with the submitted object as data
- `mapping`: text
- `data`: an object with any data
- `visibleIf`: text
- `title`: text. Heading above the fields
- `buttonTitle`: text. The submit button's text; Submit by default
- `formElements` **(required)**: an array; each item is a form field. The fields; each one puts its value under its mapping in the submitted object

#### `check`

A tick box, ticked when the data's true/false value at mapping is true. Ticking the box runs its action at once with that value turned over, so an update event saves it; a ticked title is struck through. Only the box ticks: a tap on the title reaches the list item.

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`). Runs when the box is ticked or unticked, with the data where the value at mapping says so; usually an update event
- `mapping` **(required)**: text (not empty). The field holding true or false, such as "done"
- `data`: an object with any data
- `visibleIf`: text
- `title`: text. The text beside the box; may use ${field} placeholders

#### `files`

The files attached with a files field: a chip each, with its name and size; a tap opens the file in an app on the phone that reads it.

- `id`: text (not empty)
- `action`: an action (or `{ "ref": "id" }`)
- `mapping` **(required)**: text (not empty). The files field's list, such as "files"
- `data`: an object with any data
- `visibleIf`: text

### Form fields

The items of a form's `formElements`.

#### `text`

- `mapping` **(required)**: text (not empty). Key of the submitted object that receives the value
- `title` **(required)**: text
- `description`: text
- `mandatory`: `true` or `false`. Must be filled in before submitting
- `placeholder`: text
- `value`: text
- `textType`: one of `"plain"`, `"numeric"`, `"email"`, `"password"`, `"url"`
- `multiline`: `true` or `false`. Takes several lines (Enter starts a new one), growing as it fills: for notes and descriptions

#### `selector`

- `mapping` **(required)**: text (not empty)
- `title` **(required)**: text
- `description`: text
- `mandatory`: `true` or `false`
- `options` **(required)**: an array; each item is text. The choices: each is a string, shown and submitted, or { "label": shown, "value": submitted }, such as { "label": "Any type", "value": "" }
- `selection`: text. The value chosen at first

#### `date`

- `mapping` **(required)**: text (not empty)
- `title` **(required)**: text
- `description`: text
- `mandatory`: `true` or `false`
- `min`: a date, `dd/MM/yyyy`
- `max`: a date, `dd/MM/yyyy`
- `value`: a date, `dd/MM/yyyy`

#### `checkbox`

- `mapping` **(required)**: text (not empty). Key of the submitted object that receives true or false
- `title` **(required)**: text
- `description`: text
- `mandatory`: `true` or `false`. Must be checked before submitting
- `value`: `true` or `false`

#### `time`

A time of day, picked on a clock and submitted as HH:mm, as a calendar event's time takes it.

- `mapping` **(required)**: text (not empty)
- `title` **(required)**: text
- `description`: text
- `mandatory`: `true` or `false`
- `value`: text. The time at first, HH:mm

#### `hidden`

A value the form sends without showing it, such as the id of the item on screen.

- `mapping` **(required)**: text (not empty). Key of the submitted object that receives the value
- `value`: text. The value, sent as text; may use ${field} placeholders filled in from the screen's data. Without it, the data's value at mapping is sent

#### `photos`

Photos picked from the phone's gallery, kept on the phone with the app. It submits a list of photo references; an image view whose mapping is that list shows them in a row.

- `mapping` **(required)**: text (not empty). Where the list of photos goes, such as "photos"
- `title` **(required)**: text (not empty)
- `description`: text
- `mandatory`: `true` or `false`. At least one photo is needed
- `max`: a whole number (at least 1). How many photos it takes; 10 by default

#### `files`

Files of any kind picked on the phone (a PDF, a document, a recording), linked rather than copied, so they open later. It submits a list of the files (name, type, size and link); a files view shows them.

- `mapping` **(required)**: text (not empty). Where the list of files goes, such as "files"
- `title` **(required)**: text (not empty)
- `description`: text
- `mandatory`: `true` or `false`. At least one file is needed
- `max`: a whole number (at least 1). How many files it takes; 10 by default

#### `tags`

Short labels the user adds one by one (Enter adds one), shown as chips; it submits a list of texts, which a stored list's filterBy offers as filter chips.

- `mapping` **(required)**: text (not empty)
- `title` **(required)**: text (not empty)
- `description`: text
- `placeholder`: text
- `mandatory`: `true` or `false`. At least one tag is needed

### Events

What an action does: the `event` of an action definition. An event's `data`, like an action's, is the data it runs with instead of the current data (a form's submitted values still come first).

#### `rest`

- `url` **(required)**: text (not empty). HTTPS address; may use ${field} placeholders from the current data
- `method`: one of `"get"`, `"post"`, `"put"`, `"delete"`. get (default), post, put or delete; post and put send the current data as a JSON body
- `headers`: an object
- `thenDo`: an event. The event to run next, with the JSON response as data
- `onError`: an event. The event to run when the request fails, instead of an alert; its data is the request's, plus error.message and, for an HTTP error, error.status
- `mapping`: text
- `data`: an object with any data

#### `show`

- `view` **(required)**: a view (or `{ "ref": "id" }`). The view to open; it receives the current data
- `mapping`: text. Path of the part of the current data the view receives
- `data`: an object with any data
- `title`: text. The opened screen's title in the app bar, instead of the app's name; may use ${field} placeholders from the data it receives

#### `alert`

- `value`: text. The message; may use ${field} placeholders
- `thenDoOK`: an event. The event to run when OK is tapped
- `thenDoKO`: an event. The event to run when the alert is cancelled
- `mapping`: text
- `data`: an object with any data

#### `calendar`

Opens the phone's calendar app on a new event, filled in; the user reviews and saves it there.

- `title` **(required)**: text (not empty). The event's title; may use ${field} placeholders
- `date` **(required)**: text (not empty). The day, dd/MM/yyyy; may be a placeholder such as ${date} from a form's date field
- `time`: text. The start time, HH:mm; without it the event lasts all day
- `durationMinutes`: a whole number (at least 1). How long a timed event lasts; 60 by default
- `location`: text. Where it happens; may use ${field} placeholders
- `description`: text. Notes for the event; may use ${field} placeholders
- `mapping`: text
- `data`: an object with any data

#### `open`

Hands an address to another app on the phone: https or http opens the browser, geo: a map, tel: the dialer (nothing is called) and mailto: an email app. No other kinds of address are opened.

- `url` **(required)**: text. An https:, http:, geo:, tel: or mailto: address, or a placeholder holding one, such as ${url}; values placeholders insert are percent-encoded
- `mapping`: text. Path of the part of the current data the address is filled in from
- `data`: an object with any data

#### `share`

Opens the phone's share sheet on a text, for the user to send to any app.

- `text` **(required)**: text (not empty). What is shared; may use ${field} placeholders, such as "${title} ${url}"; a line whose placeholders all have no value is left out
- `photos`: text. Path of the photos to share with the text, such as "photos" (a photos field's list): they go as attachments, the text as their caption
- `mapping`: text. Path of the part of the current data the text is filled in from
- `data`: an object with any data

#### `save`

Saves the current data (a form's submitted values) on the phone as a new object of a collection; a list with stored shows the collection. Each object gets an id and createdAt (epoch milliseconds). A form saved this way starts empty, not filled in from the screen's data (only its hidden fields take that, such as an app's id), and empty again after each save.

- `collection` **(required)**: text (not empty). The collection to add to, such as "ideas"; each app has its own
- `fields`: an object. Values to add to the object; text may use ${field} placeholders from the data the form showed (the screen's, or a list row's), then from the submitted values, such as { "appId": "${id}" }
- `unique`: text. A field that tells saved objects apart, such as "key": saving an object with the same value there replaces the one already saved (keeping its id) instead of adding another
- `thenDo`: an event. The event to run next, with the saved object as data
- `mapping`: text. Path of the part of the current data to save
- `data`: an object with any data

#### `update`

Changes a saved object: the current data (a form's submitted values, or a ticked check's item) and fields replace its values; a form's field left empty is removed from it. Its id and createdAt never change.

- `collection` **(required)**: text (not empty). The collection the object is in
- `id`: text (not empty). The id of the object to change; "${id}" (the current data's id) by default
- `fields`: an object. Values to set too; text may use ${field} placeholders, filled as in a save event
- `thenDo`: an event. The event to run next, with the changed object as data
- `mapping`: text. Path of the part of the current data to save
- `data`: an object with any data

#### `delete`

Removes saved objects: the one whose id is id (the current data's by default), or every one where matches. Put it behind an alert's thenDoOK to ask first.

- `collection` **(required)**: text (not empty). The collection to remove from
- `id`: text (not empty). The id of the object to remove; "${id}" (the current data's id) by default
- `where`: an object. Instead of id: removes every object whose fields have these values, with ${field} placeholders from the current data, such as { "appId": "${id}" }
- `thenDo`: an event. The event to run next, with the current data
- `mapping`: text. Path of the part of the current data the id or where is filled in from
- `data`: an object with any data

#### `back`

Closes the current screen and goes back to the one that opened it, as the phone's Back does; such as after saving an edit.

- `mapping`: text
- `data`: an object with any data

#### `copy`

Copies a text to the clipboard, to paste anywhere (a chat, a note); the phone confirms it.

- `text` **(required)**: text (not empty). What is copied; may use ${field} placeholders, and a line whose placeholders all have no value is left out, such as "${title}\n${description}"
- `mapping`: text. Path of the part of the current data the text is filled in from
- `data`: an object with any data

#### `pdf`

Makes a PDF of saved objects, such as the ideas not done yet: a title, then each object's text as Markdown with its photos below. The share sheet opens on the PDF, to send, save or print it.

- `collection` **(required)**: text (not empty). The collection whose objects it shows
- `where`: an object. Only the objects whose fields have these values; values may use ${field} placeholders from the current data, such as { "appId": "${id}", "done": false }
- `sort`: a sort, `{ "by": "field" }` (see Order of a stored list), or a list of sorts. The order, as a stored list's sort
- `title`: text. The heading at the top and the file's name; may use ${field} placeholders from the current data. The app's name by default
- `text` **(required)**: text (not empty). Each object's part, as Markdown with ${field} placeholders from the object, such as "## ${title}\n${description}"; a line whose placeholders all have no value is left out
- `photos`: text. Path of each object's photos (a photos field's list) to show below its text
- `files`: text. Path of each object's attached files (a files field's list), listed by name below it
- `emptyText`: text. What an alert says when no object matches, instead of an empty PDF; may use ${field} placeholders
- `pageless`: `true` or `false`. true: one page as tall as everything, best read on a phone; false (default): A4 pages, for printing
- `mapping`: text. Path of the part of the current data to use
- `data`: an object with any data

#### `textpack`

Makes a TextPack of saved objects: one Markdown text (a title, then each object's text) with their photos as files beside it, zipped (textbundle.org), for notes apps and Markdown editors. The share sheet opens on it.

- `collection` **(required)**: text (not empty). The collection whose objects it shows
- `where`: an object. Only the objects whose fields have these values; values may use ${field} placeholders from the current data, such as { "appId": "${id}", "done": false }
- `sort`: a sort, `{ "by": "field" }` (see Order of a stored list), or a list of sorts. The order, as a stored list's sort
- `title`: text. The heading at the top and the pack's name; may use ${field} placeholders from the current data. The app's name by default
- `text` **(required)**: text (not empty). Each object's part, as Markdown with ${field} placeholders from the object, such as "## ${title}\n${description}"; a line whose placeholders all have no value is left out
- `photos`: text. Path of each object's photos (a photos field's list): kept ones go in the pack as files, linked below its text
- `files`: text. Path of each object's attached files (a files field's list): they go in the pack, linked below its text
- `emptyText`: text. What an alert says when no object matches, instead of an empty pack; may use ${field} placeholders
- `mapping`: text. Path of the part of the current data to use
- `data`: an object with any data

### Actions and references

Where an action is expected, write an action definition or `{ "ref": "id" }` referencing one in the root `actions`.

#### Action definition

- `id` **(required)**: text (not empty)
- `data`: an object with any data
- `type`: one of `"click"`, `"longclick"`
- `event` **(required)**: an event

#### REST data for a list (`initData`)

- `url` **(required)**: text (not empty)
- `method`: one of `"get"`, `"post"`, `"put"`, `"delete"`. get (default), post, put or delete; post and put send the current data as a JSON body
- `headers`: an object
- `thenDo`: an event
- `mapping`: text
- `data`: an object with any data
- `nextPage`: a value. How the list asks for more items when it nears its end. Either parameter and step: that query parameter (such as skip) grows by step, the page size, until a page has fewer items. Or urlFrom: the path in the response of the next page's URL, until there is none

#### Saved data for a list (`stored`)

The objects a save event put in a collection, oldest first; it shows them again when that collection changes.

- `collection` **(required)**: text (not empty). The collection's name, as in the save event
- `where`: an object. Only the objects whose fields have these values; values may use ${field} placeholders from the list's data, such as { "appId": "${id}" }
- `sort`: a sort, `{ "by": "field" }` (see Order of a stored list), or a list of sorts. The order: one sort, or a list where each later sort orders what the earlier ones leave tied (such as done last, then by priority)
- `sortOptions`: an array; each item is an object. Orders the user can choose, as a row of buttons above the list (the first until they choose); they replace sort. Keep titles short, such as Status, Priority, Newest
- `filterBy`: text. A field of tags (a tags field's list, or text with commas): the list offers a chip per tag in use, after All, to show only items with that tag
- `initialItems`: an array; each item is an object. Objects saved in the collection the first time the list opens, when nothing was ever saved there: a checklist to tick off (places to visit, things to pack), whose items update and delete then change like any saved object. Give each a key field and set unique so a later version's new items are added too
- `unique`: text. A field of initialItems that tells them apart, such as "key": each time the list opens, the initial items whose value there was never added are saved (a later version's new places); saved ones take a later version's values (a fixed photo, a new tip) wherever the user didn't change them, so ticks and edits stay, and deleted ones don't come back

#### Order of a stored list (`sort`)

By the field by; values listed in order come first, in that order (such as ["High", "Medium", "Low"]), then the others, numbers by size and text alphabetically. Objects without the field come last; ties keep the order they were saved in.

- `by` **(required)**: text (not empty). The field to sort by, such as "priority"
- `order`: an array; each item is text. Values in the order they come first, always as text, also for numbers and true/false (such as ["false", "true"])
- `descending`: `true` or `false`. Turns the order round

#### Size (`size`)

- `width`: a whole number (at least 0)
- `height`: a whole number (at least 0)

## Example 1: a checklist saved on the phone, with a detail screen

The list starts with its `initialItems`, saved once; `unique` adds the ones a later version brings. Ticking runs the `tick` update, and `sort` puts visited places last. Tapping an item shows the reusable `place` view.

```json
{
  "schemaVersion": 3,
  "name": "Budapest checklist",
  "icon": "🇭🇺",
  "author": "Appkin",
  "version": "1.0.0",
  "mainView": {
    "type": "list",
    "stored": {
      "collection": "places",
      "unique": "key",
      "sort": [
        {
          "by": "visited"
        },
        {
          "by": "order"
        }
      ],
      "initialItems": [
        {
          "key": "parliament",
          "name": "Parliament",
          "description": "The Gothic Revival seat of the National Assembly, on the Danube.",
          "tickets": "https://www.parlament.hu/web/visitor-centre",
          "order": 1,
          "visited": false
        },
        {
          "key": "bastion",
          "name": "Fisherman's Bastion",
          "description": "Neo-Gothic terraces with the best view over Pest.",
          "order": 2,
          "visited": false
        },
        {
          "key": "matthias",
          "name": "Matthias Church",
          "description": "Coloured roof tiles in the heart of the Castle District.",
          "tickets": "https://www.matyas-templom.hu/",
          "order": 3,
          "visited": false
        }
      ]
    },
    "itemView": {
      "type": "layout",
      "views": [
        {
          "type": "check",
          "mapping": "visited",
          "title": "${name}",
          "action": {
            "ref": "tick"
          }
        }
      ]
    },
    "action": {
      "id": "open_place",
      "event": {
        "type": "show",
        "title": "${name}",
        "view": {
          "ref": "place"
        }
      }
    }
  },
  "views": [
    {
      "id": "place",
      "type": "layout",
      "views": [
        {
          "type": "text",
          "label": "${name}",
          "title": "${description}",
          "labelStyle": "bold"
        },
        {
          "type": "check",
          "mapping": "visited",
          "title": "Visited",
          "action": {
            "ref": "tick"
          }
        },
        {
          "type": "button",
          "title": "Tickets (official site)",
          "icon": "open",
          "visibleIf": "tickets",
          "action": {
            "id": "tickets",
            "event": {
              "type": "open",
              "url": "${tickets}"
            }
          }
        }
      ]
    }
  ],
  "actions": [
    {
      "id": "tick",
      "event": {
        "type": "update",
        "collection": "places"
      }
    }
  ]
}
```

## Example 2: a form with required fields and an alert

Submitting runs the form's action with the filled-in values, so the alert can use `${name}`.

```json
{
  "schemaVersion": 3,
  "name": "Summer Run 2026",
  "icon": "🏃",
  "version": "1.0.0",
  "author": "Appkin",
  "mainView": {
    "type": "layout",
    "views": [
      {
        "type": "text",
        "label": "Summer Run · 10 km",
        "title": "Saturday 13 June, 8:00 · Villa Borghese, Rome",
        "textStyle": "bold",
        "textSize": 20
      },
      {
        "type": "form",
        "buttonTitle": "Sign me up",
        "action": {
          "ref": "confirm"
        },
        "formElements": [
          {
            "type": "text",
            "mapping": "name",
            "title": "Full name",
            "placeholder": "Your name",
            "mandatory": true
          },
          {
            "type": "text",
            "mapping": "email",
            "title": "Email",
            "placeholder": "you@example.com",
            "textType": "email",
            "mandatory": true,
            "description": "We send your race number here"
          },
          {
            "type": "selector",
            "mapping": "distance",
            "title": "Distance",
            "options": [
              "5 km",
              "10 km",
              "Half marathon"
            ],
            "selection": "10 km"
          },
          {
            "type": "selector",
            "mapping": "shirt",
            "title": "T-shirt size",
            "options": [
              "S",
              "M",
              "L",
              "XL"
            ],
            "selection": "M"
          },
          {
            "type": "date",
            "mapping": "birth",
            "title": "Date of birth",
            "description": "Runners must be 16 or older"
          },
          {
            "type": "checkbox",
            "mapping": "rules",
            "title": "I accept the race rules",
            "mandatory": true
          }
        ]
      }
    ]
  },
  "actions": [
    {
      "id": "confirm",
      "type": "click",
      "event": {
        "type": "alert",
        "value": "See you at the start line, ${name}!"
      }
    }
  ]
}
```

## Example 3: a list loaded from a REST API, with a detail screen

`initData` loads the JSON; `mapping` selects its array; each item's fields fill the item view and the detail.

```json
{
  "schemaVersion": 3,
  "name": "PUBLIC DOMAIN ART",
  "version": "1.0",
  "author": "Appkin",
  "changelog": "Public-domain paintings from the Cleveland Museum of Art. Data and images from its Open Access API (CC0).",
  "mainView": {
    "id": "art_home",
    "type": "layout",
    "views": [
      {
        "type": "text",
        "label": "Public domain art",
        "title": "Paintings from the Cleveland Museum of Art. Tap one to see it larger.",
        "textStyle": "bold",
        "textSize": 18
      },
      {
        "type": "list",
        "mapping": "data",
        "initData": {
          "url": "https://openaccess-api.clevelandart.org/api/artworks/?cc0=1&has_image=1&type=Painting&limit=30&fields=id,title,creation_date,technique,creators,images",
          "method": "get"
        },
        "action": {
          "id": "show_artwork_detail"
        },
        "itemView": {
          "id": "artwork_list_item",
          "type": "layout",
          "orientation": "horizontal",
          "views": [
            {
              "type": "image",
              "url": "${images.web.url}",
              "size": {
                "width": 112,
                "height": 112
              }
            },
            {
              "type": "text",
              "label": "${title}",
              "title": "${creation_date}"
            }
          ]
        }
      },
      {
        "type": "text",
        "title": "Data and images: Cleveland Museum of Art Open Access (CC0)",
        "textStyle": "italic"
      }
    ]
  },
  "views": [
    {
      "id": "artwork_detail",
      "type": "layout",
      "views": [
        {
          "type": "image",
          "url": "${images.web.url}",
          "size": {
            "height": 300
          }
        },
        {
          "type": "text",
          "label": "${title}",
          "title": "${creation_date}",
          "textStyle": "bold",
          "textSize": 24
        },
        {
          "type": "text",
          "label": "Artist",
          "mapping": "creators.0.description"
        },
        {
          "type": "text",
          "label": "Technique",
          "mapping": "technique"
        }
      ]
    }
  ],
  "actions": [
    {
      "id": "show_artwork_detail",
      "type": "click",
      "event": {
        "type": "show",
        "view": {
          "ref": "artwork_detail"
        }
      }
    }
  ]
}
```

## Your task

Create an Appkin v3 configuration for this app: <describe the app: its screens, data and actions>

If you can create files, give me the complete configuration as a downloadable file named app.json, containing only the JSON. If you can't, reply with only the complete JSON, starting with { and ending with }: no comments or explanations. I will open your file or share your answer in the Appkin app, which shows it; if it reports problems, I will send them to you to fix.
