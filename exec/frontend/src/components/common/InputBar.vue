<template>
  <v-text-field
    :placeholder="placeholder"
    :type="type"
    :disabled="isDisabled"
    :rules="rules"
    outlined
    color="#673fb4"
    v-model="input"
    @input="passEvent"
    @change="passEvent"
    @keyup.enter="pressEnter"
  ></v-text-field>
</template>

<script>
export default {
  name: "InputBar",
  props: ["placeholder", "type", "isDisabled", "rules"],
  data: function() {
    return {
      input: "",
    };
  },
  methods: {
    passEvent: function() {
      this.$emit("pass-input", this.input);
    },
    pressEnter() {
      this.$emit("pressEnter");
    },
  },
};
</script>

<style scoped>
.v-text-field--outlined >>> fieldset {
  border-color: #e0e0e0;
  border-radius: 4px;
  border-width: 3.8px;
  width: 100%;
  /* font-size: 5px; */
}
</style>
