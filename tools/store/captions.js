// Store captions, one entry per screenshot (the listings are English only).
// `code` shows a JSON excerpt beside the phone; `prompt` puts a chat bubble above it; `sub` is a line under the headline.
const CAPTIONS = {
  en: {
    gallery: {
      kicker: "How Appkin works", headline: "A JSON file in, ready screens out", split: "gallery",
      note: "Appkin draws the file with its own built-in screens. Nothing in it runs as code.",
    },
    detail: { kicker: "Lists and pages", headline: "Tap an item to see its own page" },
    ideas: { kicker: "Saved on your phone", headline: "Notes with photos, tags and files" },
    ai: {
      kicker: "With your AI app", headline: "Describe it, your AI app writes the JSON",
      sub: "No AI inside: copy the request into your own AI app, then paste its answer back.",
    },
    home: { kicker: "Your files", headline: "Every JSON you open, kept in one place" },
    form: { kicker: "Forms", headline: "Sign-ups that check every field" },
    shortcut: { kicker: "On your home screen", headline: "Pin any of them as its own icon" },
    editor: { kicker: "No code inside", headline: "Just JSON you can read and change" },
  },
  // App Store changes to the captions above (render-ios.sh): the iPhone shots attach links rather than files.
  ios: {
    ideas: { headline: "Notes with photos, tags and links" },
  },
};

// Excerpts of examples/v3_masterpieces.json and examples/v3_race_signup.json, shortened to fit.
const CODE = {
  gallery: `{
  "name": "Masterpieces",
  "mainView": {
    "type": "layout",
    "views": [
      { "type": "text",
        "label": "Renaissance" },
      { "type": "list",
        "orientation": "horizontal",
        "mapping": "renaissance",
        "itemView": {
          "type": "layout",
          "views": [
            { "type": "image",
              "url": "\${thumb}" },
            { "type": "text",
              "title": "\${title}" },
            { "type": "text",
              "title": "\${artist}" }
          ]
        }
      }
    ],
    "data": {
      "renaissance": [
        { "title": "Mona Lisa",
          "artist": "Leonardo da Vinci",
          "thumb": "https://…" },
        { "title": "The Birth of Venus",
          "artist": "Sandro Botticelli",
          "thumb": "https://…" }
      ]
    }
  }
}`,
  form: `{
  "name": "Summer Run 2026",
  "mainView": {
    "type": "layout",
    "views": [{
      "type": "form",
      "buttonTitle": "Sign me up",
      "formElements": [
        { "type": "text",
          "mapping": "email",
          "textType": "email",
          "title": "Email" },
        { "type": "selector",
          "mapping": "distance",
          "options": ["5 km", "10 km",
                      "Half marathon"] },
        { "type": "date",
          "mapping": "birth" }
      ]
    }]
  }
}`,
};
